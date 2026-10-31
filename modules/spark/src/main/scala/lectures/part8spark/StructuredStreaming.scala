package lectures.part8spark

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger

object StructuredStreaming {
  private def removeTree(path: Path): Unit = {
    val paths = Files.walk(path)
    try {
      val iterator = paths.sorted(java.util.Comparator.reverseOrder[Path]()).iterator()
      while (iterator.hasNext) Files.delete(iterator.next())
    } finally paths.close()
  }

  def wordCounts(spark: SparkSession): Map[String, Long] = {
    val workspace = Files.createTempDirectory("scala-spark-stream-")
    val table = "word_counts_" + UUID.randomUUID().toString.replace("-", "")
    try {
      val input = Files.createDirectory(workspace.resolve("input"))
      Files.write(input.resolve("words.txt"), "scala spark scala\nspark flink\n".getBytes(StandardCharsets.UTF_8))

      // readStream produces an incremental plan, unlike a bounded read.
      val counts = spark.readStream.format("text").load(input.toString)
        .select(explode(split(col("value"), "\\s+")).as("word"))
        .groupBy("word").count()

      val query = counts.writeStream
        .format("memory") // Inspectable local demonstration, NOT a durable production sink.
        .queryName(table)
        .outputMode("complete")
        .option("checkpointLocation", workspace.resolve("checkpoint").toString)
        .trigger(Trigger.AvailableNow()) // Process available files, then terminate.
        .start()
      try {
        if (!query.awaitTermination(60000L))
          throw new IllegalStateException("Streaming demo did not finish within 60 seconds")
        spark.table(table).collect().map(row => row.getString(0) -> row.getLong(1)).toMap
      } finally query.stop()
    } finally {
      spark.catalog.dropTempView(table)
      removeTree(workspace)
    }
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkBasics.session("structured-streaming-word-count")
    try wordCounts(spark).toSeq.sortBy(_._1).foreach(println)
    finally spark.stop()
  }
}
