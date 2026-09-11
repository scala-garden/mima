package foo
class C {
  private[foo] def f(s: String): Int = s.length
  private def f(d: Double, i: Int): Int = i
  def g: Int = f("a") + f(1.0, 2)
}
