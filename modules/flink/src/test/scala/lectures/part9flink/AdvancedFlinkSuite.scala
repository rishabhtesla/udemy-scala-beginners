package lectures.part9flink

import munit.FunSuite
import scala.concurrent.duration._

class AdvancedFlinkSuite extends FunSuite {
  override val munitTimeout: Duration = 120.seconds

  test("controlled watermarks fire timers, revise a late window and route data after cleanup") {
    assertEquals(AdvancedEventTime.results(), Seq(
      "rejected-late:3000",
      "rejected-late:4000",
      "side-output:4000:8",
      "timer:scala:9999:3",
      "window:scala:0:3",
      "window:scala:0:7"
    ))
  }
}
