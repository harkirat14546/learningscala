package com.harkirat.spark.performance.reports

import org.apache.spark.sql.SparkSession
import com.typesafe.config.{Config,ConfigFactory}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.functions._

object CheckpointDirectory extends App {
  // Logger.getLogger("org").setLevel(Level.ALL)
  val props:Config=ConfigFactory.load().getConfig("mediafirst")

  val spark = SparkSession.builder().appName("serviceperfomancereports").master("local[*]").getOrCreate()
  spark.sparkContext.hadoopConfiguration.set("fs.s3n.impl", "org.apache.hadoop.fs.s3native.NativeS3FileSystem")
  spark.sparkContext.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", "AKIA3CCTHINH5JB33A62")
  spark.sparkContext.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey", "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")
  import spark.implicits._
  spark.sparkContext.setLogLevel("ERROR")
  val df = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "localhost:9092")
    .option("subscribe", "elasticsearchtopicperfs").
  load().selectExpr("CAST(value as String) as value").toDF()
df.printSchema()
  val ds = df.select(get_json_object($"value","$.Payload").alias("Payload")).toDF()
  ds.printSchema()
  val group=props.getString("input.logs.filter.group").split(",")
  //val groups=List(group)
  println(group)
  val filtergroup=ds.where(get_json_object($"Payload","$.Group").isin(group: _*))



  val finalselect = filtergroup.select(get_json_object($"Payload","$.Group").alias("Group"),
    get_json_object($"Payload","$.StartTimeUtc").alias("StartTimeUtc")
  )

  val ds1 = finalselect
    .writeStream.format("kafka")
    .option("kafka.bootstrap.servers", "localhost:9092")
   .option("topic", "testtopic")
   //.option("checkpointLocation", "s3n://checkpointkafkastructure/")
    .option("checkpointLocation", "/etc/checkpoint/test")
   .trigger(Trigger.ProcessingTime("60 seconds"))
   .outputMode(OutputMode.Update)
   .start().awaitTermination()


}