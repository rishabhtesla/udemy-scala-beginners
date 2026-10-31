package lectures.part9flink

import java.time.Duration
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.tuple.Tuple3
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import scala.jdk.CollectionConverters._

object EventTimeWindows {
  // (word, event timestamp in milliseconds, quantity); Java tuples have explicit
  // Flink type information, avoiding erased Scala generic/closure inference.
  type Event = Tuple3[String, java.lang.Long, java.lang.Integer]
  private val eventType = Types.TUPLE[Event](Types.STRING, Types.LONG, Types.INT)

  class WordKey extends KeySelector[Event, String] {
    override def getKey(event: Event): String = event.f0
  }

  class WindowTotal extends ProcessWindowFunction[Event, String, String, TimeWindow] {
    override def process(
        key: String,
        context: ProcessWindowFunction[Event, String, String, TimeWindow]#Context,
        events: java.lang.Iterable[Event],
        out: Collector[String]
    ): Unit = {
      // This introductory implementation buffers the window's records. Use an
      // incremental AggregateFunction for large windows to keep state smaller.
      val total = events.asScala.map(_.f2.intValue()).sum
      out.collect(s"$key:[${context.window().getStart},${context.window().getEnd})=$total")
    }
  }

  def totals(): Seq[String] = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)

    val source = env.fromData(
      eventType,
      Tuple3.of("scala", Long.box(1000L), Int.box(1)),
      Tuple3.of("scala", Long.box(3000L), Int.box(1)),
      Tuple3.of("scala", Long.box(2000L), Int.box(1)), // Out of timestamp order.
      Tuple3.of("scala", Long.box(11000L), Int.box(2))
    )
    val watermarks = WatermarkStrategy.forBoundedOutOfOrderness[Event](Duration.ofSeconds(2))
      .withTimestampAssigner(new SerializableTimestampAssigner[Event] {
        override def extractTimestamp(event: Event, previous: Long): Long = event.f1.longValue()
      })

    val results = source.assignTimestampsAndWatermarks(watermarks)
      .keyBy(new WordKey)
      .window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))
      .process(new WindowTotal)
      .executeAndCollect("event-time-windows")
    // A bounded source emits a final watermark, closing the last window too.
    try results.asScala.toVector.sorted
    finally results.close()
  }

  def main(args: Array[String]): Unit = totals().foreach(println)
}
