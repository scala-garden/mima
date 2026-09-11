package foo
sealed trait T { def a: Int }
final class Impl extends T { def a = 1 }
