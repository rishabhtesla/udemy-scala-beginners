package lectures.part9flink

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.functions.{FlatMapFunction, ReduceFunction}
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.tuple.Tuple2
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import scala.jdk.CollectionConverters._

object DataStreamBasics {
  type Count = Tuple2[String, java.lang.Integer]
  val countType = Types.TUPLE[Count](Types.STRING, Types.INT)

  class WordKey extends KeySelector[Count, String] {
    override def getKey(value: Count): String = value.f0
  }

  def wordCounts(): Map[String, Int] = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(2)
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val words = env.fromData("scala flink scala", "flink akka")
      .flatMap(new FlatMapFunction[String, Count] {
        override def flatMap(line: String, out: Collector[Count]): Unit =
          line.split("\\s+").foreach(word => out.collect(Tuple2.of(word, Int.box(1))))
      }).returns(countType)

    // keyBy partitions the stream by key. In BATCH mode the bounded reduce
    // produces a final count per key, rather than streaming partial updates.
    val counts = words.keyBy(new WordKey).reduce(new ReduceFunction[Count] {
      override def reduce(left: Count, right: Count): Count =
        Tuple2.of(left.f0, Int.box(left.f1.intValue() + right.f1.intValue()))
    })

    // executeAndCollect submits the graph and streams results back to this JVM.
    val results = counts.executeAndCollect("bounded-word-count")
    try results.asScala.map(value => value.f0 -> value.f1.intValue()).toMap
    finally results.close()
  }

  def main(args: Array[String]): Unit =
    wordCounts().toSeq.sortBy(_._1).foreach(println)
}
