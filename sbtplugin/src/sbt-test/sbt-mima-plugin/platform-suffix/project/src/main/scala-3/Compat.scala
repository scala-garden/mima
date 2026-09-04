import sbt.*, Keys.*
import sbt.librarymanagement.Platform
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.*

object Compat {
  val checkPreviousArtifact = taskKey[Unit]("the previous artifact resolves for this project's platform")

  val settings = Seq(
    platform := Platform.sjs1,
    mimaPreviousArtifacts := Set("org.scala-js" %% "scalajs-dom" % "2.8.0"),
    checkPreviousArtifact := {
      val resolved = mimaPreviousClassfiles.value.keys.map(_.name).toList
      assert(resolved == List("scalajs-dom_sjs1_3"), resolved)
    },
  )
}
