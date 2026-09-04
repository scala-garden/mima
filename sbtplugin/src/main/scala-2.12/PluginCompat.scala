// scalafmt: { spaces.afterSymbolicDefs = true }
package com.typesafe.tools.mima
package plugin

import sbt.*

object PluginCompat {
  // sbt 1 has no platform setting: the Scala.js and Native plugins put it in `crossVersion`
  private[plugin] val platformed: Def.Initialize[ModuleID => ModuleID] = Def.setting((m: ModuleID) => m)

  def toOldClasspath(cp: Seq[Attributed[File]]): Seq[Attributed[File]] = cp

  // This adds `Def.uncached(...)`
  implicit class DefOp(singleton: Def.type) {
    def uncached[A1](a: A1): A1 = a
  }

  // Used to differentiate unset mimaPreviousArtifacts from empty mimaPreviousArtifacts
  private[plugin] object NoPreviousArtifacts  extends EmptySet[ModuleID]
  private[plugin] object NoPreviousClassfiles extends EmptyMap[ModuleID, File]

  private[plugin] sealed class EmptySet[A] extends Set[A] {
    def iterator          = Iterator.empty
    def contains(elem: A) = false
    def + (elem: A)       = Set(elem)
    def - (elem: A)       = this

    override def size                  = 0
    override def foreach[U](f: A => U) = ()
    override def toSet[B >: A]: Set[B] = this.asInstanceOf[Set[B]]
  }

  private[plugin] sealed class EmptyMap[K, V] extends Map[K, V] {
    def get(key: K)              = None
    def iterator                 = Iterator.empty
    def + [V1 >: V](kv: (K, V1)) = updated(kv._1, kv._2)
    def - (key: K)               = this

    override def size                                       = 0
    override def contains(key: K)                           = false
    override def getOrElse[V1 >: V](key: K, default: => V1) = default
    override def updated[V1 >: V](key: K, value: V1)        = Map(key -> value)

    override def apply(key: K) = throw new NoSuchElementException(s"key not found: $key")
  }
}
