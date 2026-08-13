scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-Xfatal-warnings",
  "-Xlint",
  "-Ywarn-unused:_,-imports",
)

addSbtPlugin("com.github.sbt" % "sbt-ci-release"  % "1.12.0")
addSbtPlugin("com.typesafe"   % "sbt-mima-plugin" % "1.1.6")

addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.4.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
