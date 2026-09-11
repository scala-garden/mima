package foo
class C {
  private def f(d: Double, i: Int): Int = i
  def g: Int = f(1.0, 2)
}
