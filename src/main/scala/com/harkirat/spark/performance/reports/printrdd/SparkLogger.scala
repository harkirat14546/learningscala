//package com.harkirat.spark.performance.reports.printrdd
//
//import com.harkirat.utils.config.ConfigUtils
//import com.harkirat.utils.logging.{Logger, LoggerToElasticsearch, LoggerToLocal}
//import com.harkirat.utils.tools.DataDef.DataMap
//import com.typesafe.config.ConfigFactory
//
//import scala.collection.mutable.ListBuffer
//import scala.util.Try
//
//
///**
// * A singleton object for logging
// *
// */
//object SparkLogger
//{
//
//  /**
//   * validates if log levels are valid
//   *
//   * @param level logging level to be validated
//   */
//  private def validateLogLevel(level: String): Unit =
//  {
//    val supportedLevels = List("TRACE","DEBUG","INFO","WARN","ERROR","TSG")
//    if (!(supportedLevels contains level.toUpperCase)) throw new IllegalArgumentException(s"log level $level is not supported")
//  }
//
//  //initiate logger using config
//  private val sparkLoggerConfig = Try(ConfigUtils.getConfig.getConfig("sparkLogger")).getOrElse(ConfigFactory.empty)
//  private val jobId = Try(ConfigUtils.getConfig.getString("jobId")).getOrElse("-")
//
//  private val loggerList = new ListBuffer[Logger]()
//
//  // add local Logger
//  if ((sparkLoggerConfig hasPath "sink.local.enable") && (sparkLoggerConfig getBoolean "sink.local.enable"))
//  {
//    val logLevel = if (sparkLoggerConfig hasPath "sink.local.level") {
//      sparkLoggerConfig getString "sink.local.level"
//    }
//    else{
//      "WARN"
//    }
//    validateLogLevel(logLevel)
//    loggerList += new LoggerToLocal(logLevel)
//  }
//
//  // add elasticsearch Logger
//  if ((sparkLoggerConfig hasPath "sink.elasticsearch.enable") && (sparkLoggerConfig getBoolean "sink.elasticsearch.enable"))
//  {
//    val logLevel = if (sparkLoggerConfig hasPath "sink.elasticsearch.level")
//    {
//      sparkLoggerConfig getString "sink.elasticsearch.level"
//    }
//    else{
//      "WARN"
//    }
//    validateLogLevel(logLevel)
//
//    val indexPrefix = if (sparkLoggerConfig hasPath "sink.elasticsearch.index")
//    {
//      sparkLoggerConfig getString "sink.elasticsearch.index"
//    }
//    else{
//      "spark_app_log"
//    }
//
//    loggerList += new LoggerToElasticsearch(logLevel, indexPrefix, jobId)
//  }
//
//  /** Critical message output
//   *
//   * @param message log message
//   * @param addOn Supplementary entities. This is a Map of String->Any to provide some structure to logs
//   */
//  def trace(message: String,addOn: DataMap = Map.empty[String, Any]) =
//  {
//    loggerList.foreach(_.logWithLevel("TRACE",message,addOn))
//  }
//
//  /**
//   * Debug output
//   *
//   * @param message log message
//   * @param addOn Supplementary entities. This is a Map of String->Any to provide some structure to logs
//   */
//  def debug(message: String,addOn: DataMap = Map.empty[String, Any]) =
//  {
//    loggerList.foreach(_.logWithLevel("DEBUG",message,addOn))
//  }
//
//  /** Log output
//   *
//   * @param message log message
//   * @param addOn Supplementary entities. This is a Map of String->Any to provide some structure to logs
//   */
//  def log(message: String,addOn: DataMap = Map.empty[String, Any]) =
//  {
//    loggerList.foreach(_.logWithLevel("INFO",message,addOn))
//  }
//
//  /** Warn output
//   *
//   * @param message log message
//   * @param addOn Supplementary entities. This is a Map of String->Any to provide some structure to logs
//   */
//  def warn(message: String,addOn: DataMap = Map.empty[String, Any]) =
//  {
//    loggerList.foreach(_.logWithLevel("WARN",message,addOn))
//  }
//
//  /** Error output
//   *
//   * @param message log message
//   * @param addOn Supplementary entities. This is a Map of String->Any to provide some structure to logs
//   */
//  def error(message: String,addOn: DataMap = Map.empty[String, Any]): Unit =
//  {
//    loggerList.foreach(_.logWithLevel("ERROR",message,addOn))
//  }
//
//  /** tsg(trouble shooting guide) output
//   *
//   * @param message log message
//   * @param addOn Supplementary entities. This is a Map of String->Any to provide some structure to logs
//   */
//  def tsg(message: String, tsgId: String, addOn: DataMap = Map.empty[String, Any]): Unit =
//  {
//    val TSG_HEADER = "************************ TROUBLESHOOTING GUIDE ************************\n"
//    loggerList.foreach(_.logWithLevel("TSG",TSG_HEADER+message,addOn ++ Map("tsgId" -> tsgId)))
//  }
//
//  /** Error output
//   *
//   * @param e exception
//   */
//  def error(e: Exception): Unit  =
//  {
//    this.error(s"the type of error is ${e.toString}")
//    this.error(e.getMessage+"\n"+e.getStackTrace.mkString("\n"))
//  }
//
//  /**
//   * stop
//   */
//  final def stop(): Unit = loggerList.foreach(_.stop())
//
//}
