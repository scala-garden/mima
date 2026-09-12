package foo

object Lib {
  private[foo] class Impl {
    def bar(x: Int) = x
  }

  def doIt = new Impl().bar(1)
}
