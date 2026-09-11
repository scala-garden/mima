package bar

class A {
  def foo[T](x: T): T = x
  def foo[T](x: T, y: T): T = y
}
