package foo

object Lib {
  private[foo] class Impl {
    def bar(x: Int, y: Int) = x + y
  }

  def doIt = new Impl().bar(1, 0)
}
