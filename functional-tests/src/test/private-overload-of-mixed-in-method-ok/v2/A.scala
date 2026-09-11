package foo
trait T { def f(x: Int): Int = x }
class C extends T {
  private def f(x: Int, y: Int): Int = x + y
  def g: Int = f(1, 2)
}
