package lectures.part9flink

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.api.java.tuple.Tuple2
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import scala.jdk.CollectionConverters._

object KeyedState {
  import DataStreamBasics._

  class RunningTotal extends KeyedProcessFunction[String, Count, String] {
    private var total: ValueState[java.lang.Integer] = _

    override def open(parameters: Configuration): Unit = {
      // Flink manages one logical value per key, not one value per function object.
      total = getRuntimeContext.getState(
        new ValueStateDescriptor[java.lang.Integer]("total", classOf[java.lang.Integer])
      )
    }

    override def processElement(
        value: Count,
        context: KeyedProcessFunction[String, Count, String]#Context,
        out: Collector[String]
    ): Unit = {
      val previous = Option(total.value()).fold(0)(_.intValue())
      val next = previous + value.f1.intValue()
      total.update(Int.box(next))
      out.collect(s"${context.getCurrentKey}:$next")
    }
  }

  def totals(): Seq[String] = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setRuntimeMode(RuntimeExecutionMode.STREAMING)
    // A checkpoint snapshots operator state AND source progress consistently.
    // This short bounded job may finish before the first checkpoint is taken.
    env.enableCheckpointing(1000L)

    val source = env.fromData(
      countType,
      Tuple2.of("scala", Int.box(1)),
      Tuple2.of("flink", Int.box(2)),
      Tuple2.of("scala", Int.box(3))
    )
    val results = source.keyBy(new WordKey).process(new RunningTotal)
      .executeAndCollect("keyed-running-total")
    try results.asScala.toVector
    finally results.close()
  }

  def main(args: Array[String]): Unit = totals().foreach(println)
}
