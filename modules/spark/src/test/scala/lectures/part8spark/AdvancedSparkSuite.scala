package lectures.part8spark

import java.nio.file.Files
import munit.FunSuite
import scala.concurrent.duration._

class AdvancedSparkSuite extends FunSuite {
  override val munitTimeout: Duration = 120.seconds

  test("left broadcast join and analytic row windows preserve unmatched rows and deterministic ranks") {
    val spark = SparkBasics.session("advanced-sql-test")
    try {
      val actual = AdvancedAnalytics.ranked(spark).collect().map(row =>
        (row.getInt(0), row.getString(2), row.getInt(4), row.getLong(5))).toVector
      assertEquals(actual, Vector((2, "Ada", 1, 50L), (1, "Ada", 2, 80L),
        (3, "Grace", 1, 40L), (4, "Unknown", 1, 10L)))
    } finally spark.stop()
  }

  test("file sink and checkpoint resume without duplicating earlier committed rows") {
    val spark = SparkBasics.session("stream-recovery-test")
    val workspace = Files.createTempDirectory("scala-spark-recovery-test-")
    try assertEquals(StreamingRecovery.demo(spark, workspace),
      Seq((1, "scala"), (2, "spark"), (3, "flink")))
    finally {
      spark.stop()
      val paths = Files.walk(workspace)
      try {
        val iterator = paths.sorted(java.util.Comparator.reverseOrder[java.nio.file.Path]()).iterator()
        while (iterator.hasNext) Files.delete(iterator.next())
      } finally paths.close()
    }
  }
}
