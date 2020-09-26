package com.mediakind.mediafirst.spark.servicerealtimereports.jobs

import com.ericsson.mediafirst.sparkutils.jobtemplates.StructuredStreamingJob
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.sql.functions._
import com.ericsson.mediafirst.data.providers.kafka.spark.SparkStructuredStreamingUtils
import com.ericsson.mediafirst.data.providers.objectstorages.spark.datafunctions.StructureStreamingUtils
import com.ericsson.mediafirst.utils.logging.SparkLogger
import org.apache.log4j.{Level, Logger}
import com.mediakind.mediafirst.spark.servicerealtimereports.reports.ServiceRealtimePerformanceReports
import org.apache.spark.sql.streaming.StreamingQueryListener
import org.apache.spark.sql.streaming.StreamingQueryListener.{QueryProgressEvent, QueryStartedEvent, QueryTerminatedEvent}

object ServiceRealtimePerfomanceMetrics extends StructuredStreamingJob {

  /** Run the ServiceRealtimePerfomanceMetrics Spark job (main entry point of the job)
   *
   * @param ss     SparkSession
   * @param config Configuration
   *
   */
  override def runSSJob(implicit ss: SparkSession, config: Config): Unit = {

    val group = config.getString("input.perfs.filter.group").split(",")

    val adapter = config.getString(" output.objectStorage.adapter")
    val connectionString = config.getString("output.checkpointDirectory.objectStorage.connectionString")
    val container = config.getString("output.checkpointDirectory.objectStorage.containerName")
    val jobId = config.getString("jobId")

    //Adding required config to SparkConf
    StructureStreamingUtils.addCheckpointDirectoryConfig(adapter,connectionString,ss)

    //checkpointLocations
    val serviceApiPerformanceCheckpointLocation = StructureStreamingUtils.getCheckpointLocation(adapter, connectionString, container, jobId, config.getString("stream.serviceApiPerformance.queryName"))
    val stsApiPerformanceCheckpointLocation = StructureStreamingUtils.getCheckpointLocation(adapter, connectionString, container, jobId, config.getString("stream.stsApiPerformance.queryName"))
    val ossproxyApiPerformanceCheckpointLocation = StructureStreamingUtils.getCheckpointLocation(adapter, connectionString, container, jobId, config.getString("stream.ossApiPerformance.queryName"))

    import ss.implicits._

    val payLoad = SparkStructuredStreamingUtils.readKafkaLogs(config.getString("input.perfs.topic"), ss).
      select(get_json_object($"value", "$.Payload").alias("Payload")).toDF().
      where(get_json_object($"Payload", "$.Group").isin(group: _*))

    val payLoadServiceApiPerformance = payLoad.where(!get_json_object($"Payload", "$.RoleName").
      isin(config.getString("input.perfs.serviceapi.filter").split(","): _*))
    val payLoadStsPerformancemetrics = payLoad.where(get_json_object($"Payload", "$.RoleName").
      isin(config.getString("input.perfs.sts.filter").split(","): _*))
    val payLoadOssproxyApiPerformancemetrics = payLoad.where(get_json_object($"Payload", "$.RoleName").
      isin(config.getString("input.perfs.oss.filter").split(","): _*))


    val makeReportserviceapiperformance = ServiceRealtimePerformanceReports.makeReportStructure(payLoadServiceApiPerformance, ss)
    val makeReportstsperformancemetrics = ServiceRealtimePerformanceReports.makeReportStructure(payLoadStsPerformancemetrics, ss)
    val makeReportossproxyapiperformancemetrics = ServiceRealtimePerformanceReports.makeReportStructure(payLoadOssproxyApiPerformancemetrics, ss)


    //query metric collection

    ss.streams.addListener(new StreamingQueryListener() {
      override def onQueryStarted(queryStarted: QueryStartedEvent): Unit = {
        println("Query started: " + queryStarted.id)
      }

      override def onQueryTerminated(queryTerminated: QueryTerminatedEvent): Unit = {
        println("Query terminated: " + queryTerminated.id)
      }

      override def onQueryProgress(queryProgress: QueryProgressEvent): Unit = {
        val queryprogress = queryProgress.progress
        SparkLogger.log(s"Publishing query Metrics: $queryprogress")
      }
    })

    SparkStructuredStreamingUtils.publishLogsToKafka(makeReportserviceapiperformance,
      config.getString("output.perfs.serviceApiPerformance.topic"),
      config.getString("stream.serviceApiPerformance.queryName"),
      serviceApiPerformanceCheckpointLocation, ss)

    SparkStructuredStreamingUtils.publishLogsToKafka(makeReportstsperformancemetrics,
      config.getString("output.perfs.stsApiPerformance.topic"),
      config.getString("stream.stsApiPerformance.queryName"),
      stsApiPerformanceCheckpointLocation, ss)

    SparkStructuredStreamingUtils.publishLogsToKafka(makeReportossproxyapiperformancemetrics,
      config.getString("output.perfs.ossApiPerformance.topic"),
      config.getString("stream.ossApiPerformance.queryName"),
      ossproxyApiPerformanceCheckpointLocation, ss)
  }
}
