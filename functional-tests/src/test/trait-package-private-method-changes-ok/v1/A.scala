package foo

trait T { private[foo] def m = 1 }
class C extends T
object Lib { def doIt = (new C).m }
