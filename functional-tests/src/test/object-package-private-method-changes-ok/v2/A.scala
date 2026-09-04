package foo

object O { private[foo] def m(x: Int) = x }
object Lib { def doIt = O.m(1) }
