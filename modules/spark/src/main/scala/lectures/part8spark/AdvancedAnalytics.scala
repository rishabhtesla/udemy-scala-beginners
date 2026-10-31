package lectures.part8spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel

object AdvancedAnalytics {
  final case class Sale(id: Int, customerId: Int, amount: Long)
  final case class Customer(customerId: Int, name: String)

  def ranked(spark: SparkSession): DataFrame = {
    import spark.implicits._
    val sales = Seq(Sale(1, 1, 30L), Sale(2, 1, 50L), Sale(3, 2, 40L), Sale(4, 99, 10L)).toDS()
    val customers = Seq(Customer(1, "Ada"), Customer(2, "Grace")).toDS()
    // A left join preserves the unknown customer; coalesce makes its display explicit.
    // Broadcasting is appropriate ONLY because this fixture dimension is tiny.
    val enriched = sales.join(broadcast(customers), Seq("customerId"), "left")
      .withColumn("name", coalesce(col("name"), lit("Unknown")))
    val ordering = Window.partitionBy("customerId").orderBy(col("amount").desc, col("id").asc)
    enriched
      .withColumn("rank", row_number().over(ordering))
      .withColumn("runningRevenue",
        sum(col("amount")).over(ordering.rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      .select("id", "customerId", "name", "amount", "rank", "runningRevenue")
      .orderBy("customerId", "rank")
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkBasics.session("advanced-analytics")
    val result = ranked(spark).persist(StorageLevel.MEMORY_AND_DISK)
    try {
      println(s"Rows: ${result.count()}") // First action populates the cache.
      result.show(truncate = false) // Second action can reuse it.
      result.explain(extended = true)
    } finally {
      result.unpersist()
      spark.stop()
    }
  }
}
