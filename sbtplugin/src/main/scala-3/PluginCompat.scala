// scalafmt: { maxColumn = 150 }
package com.typesafe.tools.mima
package plugin

import sbt.*
import xsbti.{ FileConverter, HashedVirtualFileRef }

object PluginCompat:
  /** sbt 2 keeps the platform out of `crossVersion`: on `Platform.sjs1` the artifact is
   *  named `foo_sjs1_3`. mima names the previous artifact itself, so it has to add that
   *  suffix before the Scala one, and drop the platform so resolution doesn't add it again. */
  private[plugin] val platformed: Def.Initialize[ModuleID => ModuleID] = Def.setting {
    val projectPlatform = Keys.platform.value
    m =>
      m.platformOpt.orElse(Some(projectPlatform)).filter(p => p.nonEmpty && p != Platform.jvm) match
        case Some(p) => m.withName(s"${m.name}_$p").withPlatformOpt(None)
        case None    => m
  }

  inline def toOldClasspath(cp: Seq[Attributed[HashedVirtualFileRef]])(using conv: FileConverter): Seq[Attributed[File]] =
    cp.map(_.map(x => conv.toPath(x).toFile))

  // Used to differentiate unset mimaPreviousArtifacts from empty mimaPreviousArtifacts
  private[plugin] object NoPreviousArtifacts  extends EmptySet[ModuleID]
  private[plugin] object NoPreviousClassfiles extends EmptyMap[ModuleID, File]

  private[plugin] sealed class EmptySet[A] extends Set[A]:
    def iterator          = Iterator.empty
    def contains(elem: A) = false
    def excl(elem: A)     = this
    def incl(elem: A)     = Set(elem)

    override def size                  = 0
    override def foreach[U](f: A => U) = ()
    override def toSet[B >: A]: Set[B] = this.asInstanceOf[Set[B]]

  private[plugin] sealed class EmptyMap[K, V] extends Map[K, V]:
    def get(key: K)     = None
    def iterator        = Iterator.empty
    def removed(key: K) = this

    override def size                                       = 0
    override def contains(key: K)                           = false
    override def getOrElse[V1 >: V](key: K, default: => V1) = default
    override def updated[V1 >: V](key: K, value: V1)        = Map(key -> value)

    override def apply(key: K) = throw new NoSuchElementException(s"key not found: $key")
end PluginCompat
