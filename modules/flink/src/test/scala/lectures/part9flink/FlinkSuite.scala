package lectures.part9flink

import munit.FunSuite

class FlinkSuite extends FunSuite {
  override val munitTimeout: scala.concurrent.duration.Duration = scala.concurrent.duration.Duration(120, "seconds")

  test("bounded batch aggregation produces one final count per key") {
    assertEquals(DataStreamBasics.wordCounts(), Map("scala" -> 2, "flink" -> 2, "akka" -> 1))
  }

  test("streaming state is isolated per key") {
    assertEquals(KeyedState.totals(), Seq("scala:1", "flink:2", "scala:4"))
  }

  test("event-time windows include out-of-order events and close at end of input") {
    assertEquals(EventTimeWindows.totals(), Seq("scala:[0,10000)=3", "scala:[10000,20000)=2"))
  }
}
