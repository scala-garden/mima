class Mine extends foo.T { def a = 1 }

object App {
  def main(args: Array[String]): Unit = println(new Mine().a)
}
