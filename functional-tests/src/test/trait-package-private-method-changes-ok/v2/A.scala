package foo

trait T { private[foo] def m(x: Int) = x }
class C extends T
object Lib { def doIt = (new C).m(1) }
