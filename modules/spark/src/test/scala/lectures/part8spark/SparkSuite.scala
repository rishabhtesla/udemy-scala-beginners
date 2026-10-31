package lectures.part8spark

import munit.FunSuite

class SparkSuite extends FunSuite {
  override val munitTimeout: scala.concurrent.duration.Duration = scala.concurrent.duration.Duration(120, "seconds")

  test("RDD, SQL and streaming plans compute the expected results") {
    val spark = SparkBasics.session("spark-test")
    try {
      assertEquals(SparkBasics.wordCounts(spark), Map("scala" -> 2, "spark" -> 2, "akka" -> 1))
      val revenue = SparkSql.revenue(spark).collect().map(row => row.getString(0) -> row.getLong(1)).toMap
      assertEquals(revenue, Map("books" -> 50L, "games" -> 60L))
      assertEquals(StructuredStreaming.wordCounts(spark), Map("scala" -> 2L, "spark" -> 2L, "flink" -> 1L))
    } finally spark.stop()
  }
}
