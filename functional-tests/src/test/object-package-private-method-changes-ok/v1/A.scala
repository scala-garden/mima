package foo

object O { private[foo] def m = 1 }
object Lib { def doIt = O.m }
