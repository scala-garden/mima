package foo

trait T { private[foo] def m = 1 }
class D extends T { override def m = 2 }
