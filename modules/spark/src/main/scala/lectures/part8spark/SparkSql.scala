package lectures.part8spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object SparkSql {
  final case class Order(id: Int, category: String, amount: Long)

  def revenue(spark: SparkSession): DataFrame = {
    import spark.implicits._
    // Encoders bridge JVM objects and Spark's internal tabular representation.
    val orders = Seq(
      Order(1, "books", 30L),
      Order(2, "books", 20L),
      Order(3, "games", 60L),
      Order(4, "games", -10L)
    ).toDS()

    orders.filter(col("amount") > 0)
      .groupBy(col("category"))
      .agg(sum(col("amount")).as("revenue"))
      .orderBy(col("category"))
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkBasics.session("sql-revenue")
    try {
      val result = revenue(spark)
      // Catalyst optimizes the logical plan; the physical plan chooses operators.
      result.explain(extended = true)
      result.show() // books -> 50, games -> 60; show is an action.
    } finally spark.stop()
  }
}
