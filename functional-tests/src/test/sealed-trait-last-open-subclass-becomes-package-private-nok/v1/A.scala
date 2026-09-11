package foo
sealed trait T { def a: Int }
class Impl extends T { def a = 1 }
object Lib { def make: T = new Impl }
