package foo
private[foo] trait Base[A] { def bar(x: Int): Int = x }
class Pub extends Base[String]
object Lib { def go: Pub = new Pub }
