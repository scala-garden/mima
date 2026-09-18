package foo
private[foo] trait I1 { def bar(x: Int): Int = x }
private[foo] trait I2 extends I1
private[foo] trait I3 extends I2
object Lib extends I3
