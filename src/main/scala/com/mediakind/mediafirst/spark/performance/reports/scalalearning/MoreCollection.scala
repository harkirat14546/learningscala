package com.mediakind.mediafirst.spark.performance.reports.scalalearning

import org.joda.time.{DateTime, DateTimeZone}

import scala.concurrent.{Future, future, promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.io.StdIn._
import cream._
import org.apache.spark.sql.SparkSession

import scala.concurrent.Await
import scala.concurrent.duration._
case class donut(name:String,price:Map[String,DateTime])
object MoreCollection {
  def main(args: Array[String]): Unit = {

    val ss=SparkSession.builder().master("local[*]").appName("explore map").getOrCreate()
    val se=List(donut("luck",Map("r1" -> DateTime.parse("2020-09-05T00:00:00.000Z"))))


    val zr=se.filter( x=> ( !x.price.contains("r1") || x.price.isEmpty))
    println(zr)

    val zl=ss.sparkContext.parallelize( 1 to 10)
    zl.mapPartitions( x =>
      List(x.next()).iterator)

  }
}

