//package com.harkirat.spark.performance.reports
//
//import com.ericsson.mediafirst.sparkutils.jobtemplates.StructuredStreamingJob
//import com.ericsson.mediafirst.utils.config.ConfigUtils
//import com.ericsson.mediafirst.sparkutils.jobtemplates.BatchJob
//import com.ericsson.mediafirst.utils.logging.SparkLogger
//import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
//import com.typesafe.config.{Config, ConfigFactory}
//import org.apache.log4j.{Level, Logger}
//import org.apache.spark.sql.streaming.{OutputMode, Trigger}
//import org.apache.spark.sql.functions._
//import com.mediakind.mediafirst.spark.performance.reports
//import org.apache.spark.{SparkConf,SparkContext}
//import scala.util.parsing.json._
//import org.apache.spark.sql.ForeachWriter
//import org.apache.spark.sql.UDFRegistration
//import org.apache.spark.sql.functions.{col,udf}
//
//
//object serviceRealtimePerfomanceMetrics extends StructuredStreamingJob {
//// Logger.getLogger("ERROR").setLevel(Level.ALL)
//
// override def runSSJob(implicit ss: SparkSession, config: Config): Unit = {
//
//  val group=config.getString("input.logs.filter.group").split(",")
//  val site =config.getString("site")
//  import ss.implicits._
//
//
//  ss.sparkContext.setLogLevel("ERROR")
//   ss.sparkContext.hadoopConfiguration.set("fs.s3n.impl", "org.apache.hadoop.fs.s3native.NativeS3FileSystem")
//   ss.sparkContext.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", "AKIA3CCTHINH5JB33A62")
//   ss.sparkContext.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey", "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")
//
//  val payLoad=ReadingKafkaFunctions.readFromKafka(ss).select(get_json_object($"value","$.Payload").alias("Payload")).toDF().
//    where(get_json_object($"Payload","$.Group").isin(group: _*))
//
//  val payLoadserviceapiperformance= payLoad.where(!get_json_object($"Payload", "$.RoleName").
//    isin("oss","sts","operatorsts"))
//  val payLoadstsperformancemetrics= payLoad.where(get_json_object($"Payload", "$.RoleName").
//                                    isin("sts", "operatorsts")).toDF()
//  val payLoadossproxyapiperformancemetrics= payLoad.where(get_json_object($"Payload", "$.RoleName").
//                                     isin("oss")).toDF()
//
//
//  val MakeReportserviceapiperformance=makeReportStructure.makeReportStructure(payLoadserviceapiperformance,ss)
//  val MakeReportstsperformancemetrics=makeReportStructure.makeReportStructure(payLoadstsperformancemetrics,ss)
// val MakeReportossproxyapiperformancemetrics=makeReportStructure.makeReportStructure(payLoadossproxyapiperformancemetrics,ss)
//
//
// /*val finalreport=MakeReportstsperformancemetrics.writeStream.format("console").
//   option("truncate",false).start()
//  val finalreport1=MakeReportserviceapiperformance.writeStream.format("console").
//    option("truncate",false).start().awaitTermination()*/
//
// val publishserviceapi = ReadingKafkaFunctions.publishLogsToKafka(MakeReportserviceapiperformance, config.getString("output.logs.serviceapiperformance.topic"),ss)
//
// /*val publishstsAuth = ReadingKafkaFunctions.publishLogsToKafka(MakeReportstsperformancemetrics,
//                       config.getString("output.logs.stsperformance.topic"),ss)
//  val publishossproxyapi = ReadingKafkaFunctions.publishLogsToKafka(MakeReportossproxyapiperformancemetrics,
//   config.getString("output.logs.ossproxyapiperformance.topic"),ss)*/
// }
//}
