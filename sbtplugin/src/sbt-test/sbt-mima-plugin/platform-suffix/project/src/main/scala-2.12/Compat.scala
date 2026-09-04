import sbt._, Keys._

object Compat {
  val checkPreviousArtifact = taskKey[Unit]("the previous artifact resolves for this project's platform")

  // sbt 1 has no platform setting: the Scala.js and Native plugins put the platform in `crossVersion`
  val settings = Seq(checkPreviousArtifact := ())
}
