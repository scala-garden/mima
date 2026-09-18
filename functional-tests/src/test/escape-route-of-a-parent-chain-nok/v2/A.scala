package foo
private[foo] trait I1 { def bar(x: Long): Int = x.toInt }
private[foo] trait I2 extends I1
private[foo] trait I3 extends I2
object Lib extends I3
