package foo

trait T { private[foo] def m(x: Int) = x }
class D extends T { override def m(x: Int) = 2 }
