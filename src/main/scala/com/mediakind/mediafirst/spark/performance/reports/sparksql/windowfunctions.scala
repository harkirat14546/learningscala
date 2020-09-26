package com.mediakind.mediafirst.spark.performance.reports.sparksql

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window
object windowfunctions extends App {

  val ss=SparkSession.builder().appName("window").master("local[*]").getOrCreate()
  case class Salary(depName: String, empNo: Int, salary: Int)
import ss.implicits._
  val dataset = Seq(
    ("Thin",       "cell phone", 6000),
    ("Normal",     "tablet",     1500),
    ("Mini",       "tablet",     5500),
    ("Ultra thin", "cell phone", 5000),
    ("Very thin",  "cell phone", 6000),
    ("Big",        "tablet",     2500),
    ("Bendable",   "cell phone", 3000),
    ("Foldable",   "cell phone", 3000),
    ("Pro",        "tablet",     4500),
    ("Pro2",       "tablet",     6500))
    .toDF("product", "category", "revenue")
  dataset.withColumn("row",dense_rank().
    over(Window.partitionBy("category").orderBy("revenue"))).filter($"row" <=2).show()
}
