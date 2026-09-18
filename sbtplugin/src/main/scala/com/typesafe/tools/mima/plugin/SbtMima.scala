// scalafmt: { maxColumn = 200, align.preset = some }
package com.typesafe.tools.mima
package plugin

import java.io.File

import com.typesafe.tools.mima.core.*
import com.typesafe.tools.mima.lib.MiMaLib
import sbt.*
import sbt.Keys.TaskStreams
import sbt.librarymanagement.{ UpdateLogging => _, * }

import scala.io.Source
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import scala.util.matching._

object SbtMima {
  /** Runs MiMa and returns a two lists of potential binary incompatibilities,
      the first for backward compatibility checking, and the second for forward checking. */
  def runMima(prev: File, curr: File, cp: Seq[Attributed[File]], dir: String, scalaVersion: String, logger: Logger, excludeAnnots: List[String]): (List[Problem], List[Problem]) =
    runMima(prev, curr, cp, dir, scalaVersion, logger, excludeAnnots, Nil)._1

  /** Runs MiMa and returns two lists of potential binary incompatibilities, the first for backward
      compatibility checking and the second for forward checking, along with the unused `binaryApi` entries */
  def runMima(prev: File, curr: File, cp: Seq[Attributed[File]], dir: String, scalaVersion: String, logger: Logger,
      excludeAnnots: List[String], binaryApi: Seq[BinaryApiEntry]): ((List[Problem], List[Problem]), Seq[BinaryApiEntry]) = {
    sanityCheckScalaVersion(scalaVersion)
    val spec = new BinaryApiSpec(binaryApi)
    val mimaLib = new MiMaLib(Attributed.data(cp), new SbtLogger(logger), spec)
    def checkBC = mimaLib.collectProblems(prev, curr, excludeAnnots)
    def checkFC = mimaLib.collectProblems(curr, prev, excludeAnnots, forwards = true)
    val problems = dir match {
      case "backward" | "backwards" => (checkBC, Nil)
      case "forward" | "forwards"   => (Nil, checkFC)
      case "both"                   => (checkBC, checkFC)
      case _                        => (Nil, Nil)
    }
    (problems, mimaLib.unusedBinaryApi)
  }

  private def sanityCheckScalaVersion(scalaVersion: String) = {
    scalaBinaryVersion(scalaVersion) match {
      case "2.12" | "2.13" | "3" => () // ok

      case _ => throw new IllegalArgumentException(s"MiMa supports Scala 2.12, 2.13 and 3, not $scalaVersion")
    }
  }

  private def scalaBinaryVersion(version: String) =
    if (version.startsWith("3.")) "3" else version.take(4)

  /** Reports binary compatibility errors for a module. */
  def reportModuleErrors(
      module: ModuleID,
      backward: List[Problem],
      forward: List[Problem],
      failOnProblem: Boolean,
      filters: Seq[ProblemFilter],
      backwardFilters: Map[String, Seq[ProblemFilter]],
      forwardFilters: Map[String, Seq[ProblemFilter]],
      logger: Logger,
      projectName: String,
  ): Unit = {
    // filters * found is n-squared, it's fixable in principle by special-casing known
    // filter types or something, not worth it most likely...
    val log = new SbtLogger(logger)

    def isReported(versionedFilters: Map[String, Seq[ProblemFilter]])(problem: Problem) = {
      ProblemReporting.isReported(module.revision, filters, versionedFilters)(problem)
    }

    val backErrors = backward.filter(isReported(backwardFilters))
    val forwErrors = forward.filter(isReported(forwardFilters))

    val count = backErrors.size + forwErrors.size
    val doLog = if (count == 0) log.verbose(_) else if (failOnProblem) log.error(_) else log.warn(_)
    def logResult(msg: String) = doLog(s"$projectName: $msg")

    if (count == 0) {
      logResult(s"Binary compatibility check against $module passed.")
    } else {
      val filteredCount = backward.size + forward.size - count
      val filteredNote = if (filteredCount > 0) s" (filtered $filteredCount)" else ""
      val msg = s"Failed binary compatibility check against $module! Found $count potential problems$filteredNote"

      logResult(msg)

      // Print problems first, then how to filter them, so that the latter is easy to copy/paste when there are many

      for (p <- backErrors) doLog(" * " + p.description("current"))
      for (p <- forwErrors) doLog(" * " + p.description("other"))

      for (line <- ProblemSuggestions.lines(backErrors, forwErrors)) doLog(line)

      if (failOnProblem)
        sys.error(msg)
    }
  }

  /** `#` is the comment of a filters file, `//` the one of a build, and a block copied from a
   *  report is pasted into either. */
  private def isComment(line: String) = { val t = line.trim; t.startsWith("#") || t.startsWith("//") }

  /** The `binary-api` file of a project, if it has one: the definitions it keeps checking. */
  def binaryApiFromFile(filtersDirectory: File, s: TaskStreams): Seq[BinaryApiEntry] = {
    val file = filtersDirectory / "binary-api"
    if (!file.exists) Nil
    else {
      val entries = new ListBuffer[BinaryApiEntry]
      val failures = new ListBuffer[String]
      val source = Source.fromFile(file)
      try
        for ((rawText, line) <- source.getLines().zipWithIndex if !isComment(rawText) && rawText.trim.nonEmpty)
          try entries += BinaryApi.parse(rawText)
          catch { case NonFatal(t) => failures += s"Error while parsing $file, line $line: ${t.getMessage}" }
      catch { case NonFatal(t) => failures += s"Couldn't load '$file': ${t.getMessage}" }
      finally source.close()
      if (failures.nonEmpty) {
        failures.foreach(s.log.error(_))
        throw new RuntimeException(s"Loading the mima binary-api file failed with ${failures.size} failures.")
      }
      entries.toSeq
    }
  }

  /** Resolves an artifact representing the previous abstract binary interface for testing. */
  def getPreviousArtifact(m: ModuleID, depRes: DependencyResolution, s: TaskStreams): File = {
    val module = depRes.wrapDependencyInModule(m)
    val uc = UpdateConfiguration().withLogging(UpdateLogging.DownloadOnly)
    val uwc = UnresolvedWarningConfiguration()
    val report = depRes.update(module, uc, uwc, s.log).left.map(_.resolveException).toTry.get
    val jars = for {
      config <- report.configurations.iterator
      module <- config.modules
      (artifact, file) <- module.artifacts
      if artifact.name == m.name
      if artifact.classifier.isEmpty
    } yield file
    jars.toList match {
      case Nil             => sys.error(s"Could not resolve previous ABI: $m")
      case jar :: moreJars =>
        if (moreJars.nonEmpty)
          s.log.debug(s"Returning $jar and ignoring $moreJars for $m")
        jar
    }
  }

  def issueFiltersFromFiles(filtersDirectory: File, fileExtension: Regex, s: TaskStreams): Map[String, Seq[ProblemFilter]] = {
    val ExclusionPattern = """ProblemFilters\.exclude\[([^\]]+)\]\("([^"]+)"\)""".r
    val mappings = mutable.HashMap.empty[String, Seq[ProblemFilter]]
      .withDefault(_ => new ListBuffer[ProblemFilter].toSeq)
    val failures = new ListBuffer[String]

    def parseOneFile(file: File): Seq[ProblemFilter] = {
      val filters = new ListBuffer[ProblemFilter]
      val source = Source.fromFile(file)
      try {
        for {
          (rawText, line) <- source.getLines().zipWithIndex
          if !isComment(rawText)
          // the report prints filters comma-separated, ready to paste into a Seq or into a file
          text = rawText.trim.stripSuffix(",")
          if text != ""
        } {
          text match {
            case ExclusionPattern(className, target) =>
              try filters += ProblemFilters.exclude(className, target)
              catch {
                case NonFatal(t) => failures += s"Error while parsing $file, line $line: ${t.getMessage}"
              }
            case _ => failures += s"Couldn't parse $file, line $line: '$text'"
          }
        }
      } catch {
        case NonFatal(t) => failures += s"Couldn't load '$file': ${t.getMessage}"
      } finally source.close()
      filters.toSeq
    }

    for {
      fileOrDir <- Option(filtersDirectory.listFiles()).getOrElse(Array.empty[File])
      extension <- fileExtension.findFirstIn(fileOrDir.getName)
      version = fileOrDir.getName.dropRight(extension.length)
      file <- Option(fileOrDir.listFiles()).getOrElse(Array(fileOrDir))
    } mappings(version) = mappings(version) ++ parseOneFile(file)

    if (failures.isEmpty) {
      mappings.toMap
    } else {
      failures.foreach(s.log.error(_))
      throw new RuntimeException(s"Loading Mima filters failed with ${failures.size} failures.")
    }
  }
}
