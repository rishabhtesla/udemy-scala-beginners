package lectures.part9flink

import java.time.Duration
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.functions.{AggregateFunction, MapFunction}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.tuple.Tuple3
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.{Collector, OutputTag}
import scala.jdk.CollectionConverters._

object AdvancedEventTime {
  type Event = Tuple3[String, java.lang.Long, java.lang.Integer]
  val eventType = Types.TUPLE[Event](Types.STRING, Types.LONG, Types.INT)
  val late = new OutputTag[Event]("too-late", eventType)

  class WordKey extends KeySelector[Event, String] {
    override def getKey(event: Event): String = event.f0
  }

  // Deliberately controlled legacy source for a finite, deterministic watermark
  // experiment on Flink 1.20. New production sources should use the Source API.
  class Timeline extends SourceFunction[Event] {
    @volatile private var running = true
    override def run(context: SourceFunction.SourceContext[Event]): Unit = {
      def event(timestamp: Long, amount: Int): Unit = if (running)
        context.collectWithTimestamp(Tuple3.of("scala", Long.box(timestamp), Int.box(amount)), timestamp)
      context.getCheckpointLock.synchronized {
        event(1000L, 1)
        event(2000L, 2)
        if (running) context.emitWatermark(new Watermark(9999L)) // Fires [0,10000).
        event(3000L, 4) // Late, but accepted before the allowed-lateness cleanup.
        if (running) context.emitWatermark(new Watermark(11999L)) // Cleanup.
        event(4000L, 8) // Too late: side output, not counted.
        if (running) context.emitWatermark(new Watermark(Long.MaxValue))
      }
    }
    override def cancel(): Unit = running = false
  }

  class Sum extends AggregateFunction[Event, java.lang.Long, java.lang.Long] {
    override def createAccumulator(): java.lang.Long = Long.box(0L)
    override def add(value: Event, sum: java.lang.Long): java.lang.Long =
      Long.box(sum.longValue() + value.f2.intValue())
    override def getResult(sum: java.lang.Long): java.lang.Long = sum
    override def merge(left: java.lang.Long, right: java.lang.Long): java.lang.Long =
      Long.box(left.longValue() + right.longValue())
  }

  class DescribeWindow extends ProcessWindowFunction[java.lang.Long, String, String, TimeWindow] {
    override def process(
        key: String,
        context: ProcessWindowFunction[java.lang.Long, String, String, TimeWindow]#Context,
        values: java.lang.Iterable[java.lang.Long],
        out: Collector[String]
    ): Unit = out.collect(s"window:$key:${context.window().getStart}:${values.iterator().next()}")
  }

  class EventTimeTotal extends KeyedProcessFunction[String, Event, String] {
    private var sum: ValueState[java.lang.Long] = _
    override def open(config: Configuration): Unit =
      sum = getRuntimeContext.getState(new ValueStateDescriptor[java.lang.Long]("sum", classOf[java.lang.Long]))

    override def processElement(
        event: Event,
        context: KeyedProcessFunction[String, Event, String]#Context,
        out: Collector[String]
    ): Unit = {
      if (event.f1.longValue() <= context.timerService().currentWatermark())
        out.collect(s"rejected-late:${event.f1}")
      else {
        sum.update(Long.box(Option(sum.value()).fold(0L)(_.longValue()) + event.f2.intValue()))
        // Registering the same timer timestamp for the same key is deduplicated.
        context.timerService().registerEventTimeTimer(9999L)
      }
    }

    override def onTimer(
        timestamp: Long,
        context: KeyedProcessFunction[String, Event, String]#OnTimerContext,
        out: Collector[String]
    ): Unit = {
      out.collect(s"timer:${context.getCurrentKey}:$timestamp:${sum.value()}")
      sum.clear()
    }
  }

  def results(): Seq[String] = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
    env.setParallelism(1)
    val source = env.addSource(new Timeline).returns(eventType).uid("controlled-events")
    val windows = source.keyBy(new WordKey)
      .window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))
      .allowedLateness(Duration.ofSeconds(2))
      .sideOutputLateData(late)
      .aggregate(new Sum, new DescribeWindow).uid("window-sums")
    val rejected = windows.getSideOutput(late).map(new MapFunction[Event, String] {
      override def map(event: Event): String = s"side-output:${event.f1}:${event.f2}"
    })
    val timers = source.keyBy(new WordKey).process(new EventTimeTotal).uid("timer-sums")
    // Branches run independently: union does NOT promise cross-branch ordering.
    val output = windows.union(rejected, timers).executeAndCollect("controlled-event-time")
    try output.asScala.toVector.sorted
    finally output.close()
  }

  def main(args: Array[String]): Unit = results().foreach(println)
}
