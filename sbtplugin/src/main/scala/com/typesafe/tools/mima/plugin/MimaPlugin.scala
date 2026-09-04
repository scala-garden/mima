// scalafmt: { maxColumn = 150, align.preset = none }
package com.typesafe.tools.mima
package plugin

import sbt.*, Keys.*
import core.*
import PluginCompat.*
import xsbti.FileConverter
import scala.annotation.nowarn

/** MiMa's sbt plugin. */
object MimaPlugin extends AutoPlugin {
  override def trigger = allRequirements

  private val NoPreviousArtifacts = PluginCompat.NoPreviousArtifacts
  private val NoPreviousClassfiles = PluginCompat.NoPreviousClassfiles

  object autoImport extends MimaKeys
  import autoImport.*

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    mimaPreviousArtifacts := NoPreviousArtifacts,
    mimaExcludeAnnotations := Nil,
    mimaBinaryIssueFilters := Nil,
    mimaFailOnProblem := true,
    mimaFailOnNoPrevious := true,
    mimaReportSignatureProblems := false,
    mimaCheckDirection := "backward",
  )

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    mimaReportBinaryIssues := {
      binaryIssuesIterator.value.foreach { case (moduleId, problems) =>
        SbtMima.reportModuleErrors(
          moduleId,
          problems._1,
          problems._2,
          mimaFailOnProblem.value,
          binaryIssueFilters.value,
          mimaBackwardIssueFilters.value,
          mimaForwardIssueFilters.value,
          streams.value.log,
          name.value,
        )
      }
    },
    mimaDependencyResolution := dependencyResolution.value,
    mimaPreviousClassfiles := {
      artifactsToClassfiles.value.toClassfiles(mimaPreviousArtifacts.value)
    },
    mimaCurrentClassfiles := (Compile / classDirectory).value,
    mimaFindBinaryIssues := binaryIssuesIterator.value.toMap,
    mimaFindBinaryIssues / fullClasspath := Def.uncached((Compile / fullClasspath).value),
    mimaBackwardIssueFilters := SbtMima.issueFiltersFromFiles(mimaFiltersDirectory.value, "\\.(?:backward[s]?|both)\\.excludes".r, streams.value),
    mimaForwardIssueFilters := SbtMima.issueFiltersFromFiles(mimaFiltersDirectory.value, "\\.(?:forward[s]?|both)\\.excludes".r, streams.value),
    mimaFiltersDirectory := (Compile / sourceDirectory).value / "mima-filters",
  )

  @deprecated("Switch to enablePlugins(MimaPlugin)", "0.7.0")
  def mimaDefaultSettings: Seq[Setting[?]] = globalSettings ++ buildSettings ++ projectSettings

  trait ArtifactsToClassfiles {
    def toClassfiles(previousArtifacts: Set[ModuleID]): Map[ModuleID, File]
  }

  trait BinaryIssuesFinder {
    def runMima(prevClassFiles: Map[ModuleID, File], checkDirection: String)
        : Iterator[(ModuleID, (List[Problem], List[Problem]))]
  }

  val artifactsToClassfiles: Def.Initialize[Task[ArtifactsToClassfiles]] = Def.task {
    val depRes = mimaDependencyResolution.value
    val taskStreams = streams.value
    val smi = scalaModuleInfo.value
    val platformed = PluginCompat.platformed.value
    _ match {
      case _: NoPreviousArtifacts.type => NoPreviousClassfiles
      case previousArtifacts =>
        previousArtifacts.iterator.map { m0 =>
          val m = platformed(m0)
          val moduleId = CrossVersion(m, smi) match {
            case Some(f) => m.withName(f(m.name)).withCrossVersion(CrossVersion.disabled)
            case None => m
          }
          moduleId -> SbtMima.getPreviousArtifact(moduleId, depRes, taskStreams)
        }.toMap
    }
  }

  val binaryIssuesFinder: Def.Initialize[Task[BinaryIssuesFinder]] = Def.task {
    val log = streams.value.log
    val currClassfiles = mimaCurrentClassfiles.value
    val cp = (mimaFindBinaryIssues / fullClasspath).value
    val sv = scalaVersion.value
    val excludeAnnots = mimaExcludeAnnotations.value.toList
    val failOnNoPrevious = mimaFailOnNoPrevious.value
    val projName = name.value
    val conv0 = fileConverter.value
    @nowarn
    implicit val conv: FileConverter = conv0

    (prevClassfiles, checkDirection) => {
      if (prevClassfiles eq NoPreviousClassfiles) {
        val msg = "mimaPreviousArtifacts not set, not analyzing binary compatibility"
        if (failOnNoPrevious) sys.error(msg) else log.info(s"$projName: $msg")
      } else if (prevClassfiles.isEmpty) {
        log.info(s"$projName: mimaPreviousArtifacts is empty, not analyzing binary compatibility.")
      }

      prevClassfiles.iterator.map { case (moduleId, prevClassfiles) =>
        moduleId -> SbtMima.runMima(
          prevClassfiles,
          currClassfiles,
          toOldClasspath(cp),
          checkDirection,
          sv,
          log,
          excludeAnnots
        )
      }
    }
  }

  private val binaryIssueFilters = Def.task {
    val noSigs = Seq(
      ProblemFilters.exclude[IncompatibleSignatureProblem]("*"),
      ProblemFilters.exclude[IncompatibleClassSignatureProblem]("*"),
    )
    mimaBinaryIssueFilters.value ++ (if (mimaReportSignatureProblems.value) Nil else noSigs)
  }

  // Allows reuse between mimaFindBinaryIssues and mimaReportBinaryIssues
  // without blowing up the Akka build's heap
  private val binaryIssuesIterator = Def.task {
    binaryIssuesFinder.value.runMima(mimaPreviousClassfiles.value, mimaCheckDirection.value)
  }
}
