package com.mediakind.mediafirst.downloadablereports.jobs
/*
import com.ericsson.mediafirst.data.providers.objectstorages.spark.api
import com.ericsson.mediafirst.sparkutils.jobtemplates.BatchSparkSessionJob
import com.typesafe.config.Config
import org.apache.spark.sql.{DataFrame, SparkSession}
import com.mediakind.mediafirst.downloadablereports.datafunctions.{TimeUtil, loadDataFromKafka}
import com.mediakind.mediafirst.downloadablereports.datafunctions.loadDataFromAzure.loadSourceDataFromAzure
import com.mediakind.mediafirst.downloadablereports.reports.ClientError
import org.apache.spark.sql.functions.get_json_object
import com.ericsson.mediafirst.data.providers.azureblobs.spark.SparkAzureBlobsProviderInterface._
import org.apache.spark.sql.functions.{col, concat, lit}
import com.ericsson.mediafirst.data.providers.azureblobs.AzureBlobsUtils.writeTextBlob


object downloadableClientReport extends BatchSparkSessionJob {
  override def runBatch(implicit ss: SparkSession, config: Config): Unit = {


    val (startTime, endTime) = TimeUtil.getStartEndTimeForDaily(config)
    val debug = config.getBoolean("debug")
    println(startTime)
    println(endTime)
    val brokerString = config.getString("input.clientLogs.brokersConnectionString")
    val startingOffsets = config.getString("input.kafka.reading.offset.level")
    val topicName = config.getString("input.clientLogs.topic")

    val encrichclientLogDF = loadDataFromKafka.loadDataFromKafkaTopics(brokerString, topicName, startingOffsets)

    import ss.implicits._
    val clientErrorDF = encrichclientLogDF.where(get_json_object($"value", "$.eventName").isin("Error")).toDF()
    val df = ClientError.makeReportStructure(clientErrorDF, ss)
    df.show()


    val reportschema = "Time\tUserId\tAccountId\tIPAddress\tAccountType\tDeviceType\tDeviceOS\tUserAgent\tErrorCode\tErrorType\tErrorMessage\tErrorDetails/Properties\ttenantId"
    val finaldf: List[String] = df.rdd.map { row =>
      val y = (row(0), row(1), row(2), row(3), row(4), row(5), row(6), row(7), row(8), row(9), row(10), row(11), row(12))
      y._1 + "\t" + y._2 + "\t" + y._3 + "\t" + y._4 + "\t" + y._5 + "\t" + y._6 + "\t" + y._7 + "\t" + y._8 + "\t" + y._9 + "\t" + y._10 + "\t" + y._11 + "\t" + y._12 + "\t" + y._13
    }.
      collect().toList
    val finalnewdf = reportschema :: finaldf
    val connectionString = config.getString("input.discovery.seriesDetails.objectStorage.connectionString")
    val containerName = "downloadablereports-cold-storage-logging-test"
    val blobName = "1500.csv"
    writeTextBlob(connectionString, containerName, blobName, finalnewdf)


    import org.apache.hadoop.fs._


    /*ss.sparkContext.hadoopConfiguration.set("fs.azure", "org.apache.hadoop.fs.azure.NativeAzureFileSystem")
    ss.sparkContext.hadoopConfiguration.set("fs.azure.account.key.discsalmrusw.blob.core.windows.net",
      "6cZFsx4Nl1bs42pBTiSgwdNf9KSjY4jmZhd1RD0U5D9cddV1z/x3lKVyGuD2EWsW5UoKMq/Cynt9RAnWooVn/w==")*/
    /*  df.write.format("csv")
        .option("header", "true").mode("overwrite").
        option("sep", ",").
        save("/tmp/test/file")*/

    val schemaList = reportschema.split("\t")
    val delimiter = "\t"
    val rowdataframe = for (i <- 0 to schemaList.length) yield s"row($i)"
    val rowinclude = rowdataframe.mkString(",")


    val tupleextract = for (i <- 1 to schemaList.length + 1) yield s"tupleextract($i)"
    val tupleinclude = tupleextract.mkString("\t")
    /*  import org.apache.hadoop.fs._
      val fs = FileSystem.get(ss.sparkContext.hadoopConfiguration)
      val file = fs.globStatus(new Path("/tmp/test/file/part*"))(0).getPath().getName()
      fs.rename(new Path("/tmp/test/file/" + file), new Path("/tmp/test/file/mydata.csv"))*/

    // val finaldf=df.collect().toList

  }

  def writetoAzure(df: DataFrame, reportschema: String,reportname:String)(implicit config: Config) = {
    val connectionString = config.getString("input.discovery.seriesDetails.objectStorage.connectionString")
    val containerName = "downloadablereports-cold-storage-logging-test"
    val blobName = "1500.csv"

    val reportschema = "Time\tUserId\tAccountId\tIPAddress\tAccountType\tDeviceType\tDeviceOS\tUserAgent\tErrorCode\tErrorType\tErrorMessage\tErrorDetails/Properties\ttenantId"
    val finaldf: List[String]= reportname match {
      case "Clienterror"=>  df.rdd.map { row =>
        val y = (row(0), row(1), row(2), row(3), row(4), row(5), row(6), row(7), row(8), row(9), row(10), row(11), row(12))
        y._1 + "\t" + y._2 + "\t" + y._3 + "\t" + y._4 + "\t" + y._5 + "\t" + y._6 + "\t" + y._7 + "\t" + y._8 + "\t" + y._9 + "\t" + y._10 + "\t" + y._11 + "\t" + y._12 + "\t" + y._13
      }.collect().toList
    }
    val finalnewdf = reportschema :: finaldf
    writeTextBlob(connectionString, containerName, blobName, finalnewdf)
  }
}
*/