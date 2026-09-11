package foo
class A {
  private class B { def x = 1 }
  private def use: B = new B
}
