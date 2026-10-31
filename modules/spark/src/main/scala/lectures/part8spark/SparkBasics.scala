package lectures.part8spark

import org.apache.spark.sql.SparkSession

object SparkBasics {
  def session(name: String): SparkSession = {
    // local[2] simulates two executor threads, not a two-machine cluster.
    val spark = SparkSession.builder()
      .appName(name)
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.driver.host", "127.0.0.1")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    spark
  }

  def wordCounts(spark: SparkSession): Map[String, Int] = {
    // An RDD is partitioned data plus lineage describing how to recompute it.
    val lines = spark.sparkContext.parallelize(Seq("scala spark scala", "spark akka"), 2)
    val counts = lines
      .flatMap(_.split("\\s+"))
      .map(word => (word, 1))
      .reduceByKey(_ + _) // Shuffle: occurrences of each key must meet on one partition.

    // Transformations above are lazy; collect is an action that launches a job.
    // Collect is safe here ONLY because the fixture is tiny.
    counts.collect().toMap
  }

  def main(args: Array[String]): Unit = {
    val spark = session("rdd-word-count")
    try wordCounts(spark).toSeq.sortBy(_._1).foreach(println)
    finally spark.stop()
  }
}
