package mimabuild

import sbt._
import sbt.librarymanagement.{ SemanticSelector, VersionNumber }
import sbt.Classpaths.pluginProjectID
import sbt.Keys._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters.exclude
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

object MimaSettings {
  // clear out mimaBinaryIssueFilters when changing this
  val mimaPreviousVersion = "1.2.0"

  val mimaSettings = Def.settings(
    mimaPreviousArtifacts := Set( // defaultProjectID uses artifacts.value which breaks it =/
      pluginProjectID.value.withRevision(mimaPreviousVersion).withExplicitArtifacts(Vector())
    ),
    mimaReportSignatureProblems := true,
    mimaBinaryIssueFilters ++= Seq(
      // The main public API is:
      // * com.typesafe.tools.mima.plugin.MimaPlugin
      // * com.typesafe.tools.mima.plugin.MimaKeys
      // * com.typesafe.tools.mima.core.ProblemFilters
      // * com.typesafe.tools.mima.core.*Problem
      // * com.typesafe.tools.mima.core.util.log.Logging

      ProblemFilters.exclude[Problem]("com.typesafe.tools.mima.core.MimaUnpickler*"),
      ProblemFilters.exclude[Problem]("com.typesafe.tools.mima.core.TastyUnpickler*"),

      // the 2.11 impl class encoding is gone. ClassInfo is private[mima] but escapes through
      // PackageInfo.classes, so its members are checked.
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo._implClass*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.implClass"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.isTrait"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.isImplClass"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.hasStaticImpl"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.emulatedConcreteMethods"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.deferredMethodsInBytecode"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.PackageInfo.setImplClasses"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.NoPackageInfo.setImplClasses"),

      // the bytecode flag accessors are now isBytecodeX, and isInterface is isTraitOrInterface
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.InfoLike.*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.isInterface"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.MethodInfo.isClassPrivate"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.MemberInfo.classPrivate*"),

      // ClassInfo.module is ClassInfo.companionClass, PackageInfo.setModules is linkModuleClasses
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo._module*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.module"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.PackageInfo.setModules"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.NoPackageInfo.setModules"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.MemberInfo.isDeprecated*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.MemberInfo.scopedPrivate*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.MemberInfo.absentFromPickle*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.MemberInfo.signature_="),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.typesafe.tools.mima.core.ClassInfo.scopedPrivateSuff"),
    ),
  )
}
