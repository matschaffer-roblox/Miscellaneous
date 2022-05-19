package ch.cern.sparkhistogram

import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.apache.spark.sql.functions.sum

/**
 * Use this class for generating data histograms using the Spark DataFrame API.
 *  - computeHistogram is a function to compute the count/frequency histogram of a given DataFrame column.
 *  - computeWeightedHistogram is a function to compute the weighted histogram of a given DataFrame column.
 *
 * @param sparkSession the active Spark session, e.g. spark when using spark-shell
 *
 */
case class Histogram(sparkSession: SparkSession) {

  /**
   * This is a function to compute the count/frequency histogram of a given DataFrame column
   *
   * @param col column name on which to compute the histogram
   * @param min min: minimum value in the histogram
   * @param max maximum value in the histogram
   * @param bins number of histogram buckets to compute
   * @param df the dataframe with the data to compute
   * @return Output DataFrame
   * Output DataFrame
   * ----------------
   *  bucket: the bucket number, range from 1 to bins (included)
   *  value: midpoint value of the given bucket
   *  count: number of values in the bucket
   */
  def computeHistogram(col: String, min: Long, max: Long, bins: Long)(df: DataFrame): DataFrame= {
    val step = (max - min) / bins
    // this will be used to fill in for missing buckets, i.e. buckets with no corresponding values
    val df_buckets= sparkSession.sql(s"select id+1 as bucket from range($bins)")

    df.
      selectExpr(s"width_bucket($col, $min, $max, $bins) as bucket").
      groupBy("bucket").
      count().
      join(df_buckets, Seq("bucket"), "right_outer"). // add missing buckets and remove buckets out of range
      selectExpr("bucket", s"$min + (bucket - 1/2) * $step as value", // use center value of the buckets
        "nvl(count, 0) as count").  // buckets with no values will have a count of 0
      orderBy("bucket")
  }

  /**
   * This is a function to compute the weighted histogram of a given DataFrame column.
   * A weighted histogram is a generalization of a frequency histogram.
   *
   * @param value_col column name on which to compute the histogram
   *                  the column needs to be of numeric type
   * @param weight_col numeric-type column with the weights,
   *                   the bucket value is computed as sum of weights.
   *                   If all weight are set to 1, you get a frequency histogram
   * @param min min: minimum value in the histogram
   * @param max maximum value in the histogram
   * @param bins number of histogram buckets to compute
   * @param df the dataframe with the data to compute
   * @return Output DataFrame
   * Output DataFrame
   * ----------------
   *  bucket: the bucket number, range from 1 to bins (included)
   *  value: midpoint value of the given bucket
   *  count: weighted sum of the number of values in the bucket
   */
  def computeWeightedHistogram(value_col: String, weight_col: String,
                               min: Long, max: Long, bins: Long)(df: DataFrame): DataFrame= {
    val step = (max - min) / bins
    // this will be used to fill in for missing buckets, i.e. buckets with no corresponding values
    val df_buckets= sparkSession.sql(s"select id+1 as bucket from range($bins)")

    df.
      selectExpr(s"width_bucket($value_col, $min, $max, $bins) as bucket", s"$weight_col").
      groupBy("bucket").
      agg(sum(s"$weight_col").alias("weighted_sum")).  // sum the weights on the weight_col.
      join(df_buckets, Seq("bucket"), "right_outer"). // add missing buckets and remove buckets out of range
      selectExpr("bucket", s"$min + (bucket - 1/2) * $step as value", // use center value of the buckets
        "nvl(weighted_sum, 0) as weighted_sum").  // buckets with no values will have a count of 0
      orderBy("bucket")
  }

}
