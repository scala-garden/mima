package foo
class A {
  protected[foo] def f: Int = 1
  protected[foo] class Inner { def g: Int = 2 }
}
