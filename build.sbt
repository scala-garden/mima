import mimabuild.*

import scala.util.Properties

inThisBuild(Seq(
  organization := "com.typesafe",
  licenses := Seq(License.Apache2),
  homepage := Some(url("http://github.com/scala-garden/mima")),
  developers := List(
    Developer("mdotta", "Mirco Dotta", "@dotta", url("https://github.com/dotta")),
    Developer("jsuereth", "Josh Suereth", "@jsuereth", url("https://github.com/jsuereth")),
    Developer("dwijnand", "Dale Wijnand", "@dwijnand", url("https://github.com/dwijnand")),
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/scala-garden/mima"), "scm:git:git@github.com:scala-garden/mima.git")),
  versionScheme := Some("early-semver"),
))

lazy val commonSettings: Seq[Setting[_]] = Seq(
  // in order to build `sbtplugin`, the `scalaVersion` of `core.jvm` has to match (2.12 or 3.8)
  scalaVersion := crossScalaVersions.value.headOption.getOrElse((LocalProject("sbtplugin") / scalaVersion).value),
  publish / skip := crossScalaVersions.value.isEmpty,
  publishLocal / skip := false, // sbtplugin/scripted requires publishLocal
  scalacOptions ++= compilerOptions(scalaVersion.value),
)

val isJdk17inCI = Properties.isJavaAtLeast("17") && sys.env.contains("CI") // to split released artifacts by JDK

def skipIfJdk17inCI[T](obj: => Seq[T]): Seq[T] = if (isJdk17inCI) Seq.empty else obj

def compilerOptions(scalaVersion: String): Seq[String] =
  Seq(
    "-feature",
    "-Wconf:cat=deprecation&msg=Stream|JavaConverters:s",
  ) ++
    (CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, _)) => Seq(
          "-Xlint",
          // these are too annoying when crossbuilding
          "-Wconf:cat=unused-nowarn:s",
        )
      case _ => Seq()
    }) ++
    (CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) => Seq("-Xsource:3")
      case Some((2, 13)) => Seq("-Xsource:3-cross")

      case _ => Seq()
    })

// Keep in sync with TestCli
val scala212 = "2.12.21"
val scala213 = "2.13.18"
val scala3_3 = "3.3.8" // keep at LTS
val scala3_8 = "3.8.4" // keep at 3.8 for sbt 2 plugin

val root = project.in(file(".")).settings(commonSettings).settings(
  name := "mima",
  crossScalaVersions := Nil,
  mimaFailOnNoPrevious := false,
)

aggregateProjects(core.jvm, core.native, cli.jvm, sbtplugin, functionalTests, integrationTests)

val munit = Def.setting("org.scalameta" %%% "munit" % "1.3.6")

val core = crossProject(JVMPlatform, NativePlatform).crossType(CrossType.Pure).settings(commonSettings).settings(
  name := "mima-core",
  crossScalaVersions := skipIfJdk17inCI(Seq(scala212, scala213, scala3_3)),
  libraryDependencies += munit.value % Test,
  MimaSettings.mimaSettings,
  apiMappings ++= {
    // WORKAROUND https://github.com/scala/bug/issues/9311
    // from https://stackoverflow.com/a/31322970/463761
    sys.props.get("sun.boot.class.path").toList
      .flatMap(_.split(java.io.File.pathSeparator))
      .collectFirst {
        case str if str.endsWith(java.io.File.separator + "rt.jar") =>
          file(str) -> url("http://docs.oracle.com/javase/8/docs/api/index.html")
      }
      .toMap
  },
).nativeSettings(mimaPreviousArtifacts := Set.empty)

val cli = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    name := "mima-cli",
    crossScalaVersions := skipIfJdk17inCI(Seq(scala212, scala213, scala3_3)),
    libraryDependencies += munit.value % Test,
    mimaFailOnNoPrevious := false,
  )
  .dependsOn(core)

val sbtplugin = project.enablePlugins(SbtPlugin).dependsOn(core.jvm).settings(commonSettings).settings(
  name := "sbt-mima-plugin",
  crossScalaVersions := { if (isJdk17inCI) Seq(scala3_8) else Seq(scala212) },
  (pluginCrossBuild / sbtVersion) := {
    scalaBinaryVersion.value match {
      case "2.12" => "1.5.8"
      case _      => "2.0.0-RC9"
    }
  },
  // drop the previous value to drop running Test/compile
  scriptedDependencies := Def.task(()).dependsOn(publishLocal, core.jvm / publishLocal).value,
  scriptedLaunchOpts += s"-Dplugin.version=${version.value}",
  scriptedLaunchOpts += s"-Dsbt.boot.directory=${file(sys.props("user.home")) / ".sbt" / "boot"}",
  MimaSettings.mimaSettings,
)

val testFunctional = taskKey[Unit]("Run the functional test")

val functionalTests = Project("functional-tests", file("functional-tests"))
  .dependsOn(core.jvm)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := skipIfJdk17inCI(Seq(scala212, scala213)),
    publish / skip := true,
    libraryDependencies += "io.get-coursier" %% "coursier" % "2.1.24",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    libraryDependencies += munit.value,
    testFunctional := (Test / test).value,
    Test / mainClass := Some("com.typesafe.tools.mima.lib.UnitTests"),
    mimaFailOnNoPrevious := false,
  )

val integrationTests = Project("integration-tests", file("integration-tests"))
  .dependsOn(functionalTests % "compile->compile")
  .settings(commonSettings)
  .settings(
    crossScalaVersions := skipIfJdk17inCI(Seq(scala212, scala213)),
    publish / skip := true,
    libraryDependencies += munit.value,
    Test / unmanagedSourceDirectories := Seq((functionalTests / baseDirectory).value / "src" / "it" / "scala"),
    Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "-b"), // disable buffering => immediate output
    mimaFailOnNoPrevious := false,
  )
