// protected[foo] is reachable from a subclass outside foo, so mima has to keep checking it
class Sub extends foo.A {
  def use: Int = f + new Inner().g
}

object App {
  def main(args: Array[String]): Unit = println(new Sub().use)
}
