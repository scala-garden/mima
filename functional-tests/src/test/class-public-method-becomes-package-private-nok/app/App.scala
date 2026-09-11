object App {
  def main(args: Array[String]): Unit = {
    println(new bar.A().foo("foo"))
    println(new bar.A().foo("foo", "bar"))
  }
}
