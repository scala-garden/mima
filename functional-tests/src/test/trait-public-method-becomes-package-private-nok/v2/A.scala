package bar
trait A {
  def foo[T](x: T): T = x
  private[bar] def bar[T](x: T): T = x
}
