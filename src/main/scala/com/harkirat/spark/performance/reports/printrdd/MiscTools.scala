package com.harkirat.spark.performance.reports.printrdd

import com.ericsson.mediafirst.utils.logging.SparkLogger
import org.apache.spark.rdd.RDD

object MiscTools
{

  /**
   * prints contents of RDD on the driver node
   * @param inputRDD input RDD
   * @param rddName name of the RDD
   * @param enabled debug flag. enable by default. If disabled, the print
   * @tparam T
   */
  def printRDD[T](inputRDD: RDD[T], rddName: String, enabled: Boolean = true, printCount: Int = 10): Unit =
  {
    if (!enabled) return
    SparkLogger.debug(s"-----------------start printing $rddName--------------")
    SparkLogger.debug(s"count of $rddName is ${inputRDD.count}")
    if (printCount > 0) {
      SparkLogger.debug(s"start printing first $printCount of $rddName")
      inputRDD.take(printCount).zipWithIndex.foreach{case(item,index) => SparkLogger.debug(s"Item $index: ${item.toString}")}
      SparkLogger.debug(s"-----------------end   printing $rddName--------------")
    }
  }
}
