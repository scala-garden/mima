package foo
class A {
  private class B { def x = 1; def y = 2 }
  private def use: B = new B
}
