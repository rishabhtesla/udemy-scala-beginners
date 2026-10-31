package lectures.part8spark

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types.{IntegerType, StringType, StructType}

object StreamingRecovery {
  val schema = new StructType().add("id", IntegerType, nullable = false)
    .add("word", StringType, nullable = false)

  def writeInput(input: Path, filename: String, json: String): Unit = {
    // Finish a file outside the watched directory, then move it in. Real file
    // sources should never expose partially written files to the reader.
    val staging = Files.createTempFile(input.getParent, "input-", ".json")
    try {
      Files.write(staging, json.getBytes(StandardCharsets.UTF_8))
      Files.move(staging, input.resolve(filename))
    } finally Files.deleteIfExists(staging)
  }

  def runAvailable(spark: SparkSession, workspace: Path): Unit = {
    val query = spark.readStream.schema(schema)
      .option("mode", "FAILFAST").json(workspace.resolve("input").toString)
      .writeStream.format("parquet").outputMode("append")
      .option("path", workspace.resolve("output").toString)
      .option("checkpointLocation", workspace.resolve("checkpoint").toString)
      .trigger(Trigger.AvailableNow()).start()
    try {
      if (!query.awaitTermination(60000L))
        throw new IllegalStateException("Recovery query did not finish within 60 seconds")
    } finally query.stop()
  }

  def demo(spark: SparkSession, workspace: Path): Seq[(Int, String)] = {
    val input = Files.createDirectory(workspace.resolve("input"))
    writeInput(input, "batch-1.json", """{"id":1,"word":"scala"}
{"id":2,"word":"spark"}
""")
    runAvailable(spark, workspace)
    writeInput(input, "batch-2.json", """{"id":3,"word":"flink"}
""")
    // Same query + input/output/checkpoint locations: resume source progress.
    runAvailable(spark, workspace)
    runAvailable(spark, workspace) // No new files: must not duplicate committed rows.
    spark.read.schema(schema).parquet(workspace.resolve("output").toString)
      .orderBy("id").collect().map(row => row.getInt(0) -> row.getString(1)).toVector
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkBasics.session("streaming-recovery")
    val workspace = Files.createTempDirectory("scala-spark-recovery-")
    try {
      println(s"Input, Parquet output and checkpoint retained at: $workspace")
      demo(spark, workspace).foreach(println)
    } finally spark.stop()
  }
}
