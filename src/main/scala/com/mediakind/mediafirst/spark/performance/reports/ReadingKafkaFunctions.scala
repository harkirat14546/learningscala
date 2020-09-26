package com.mediakind.mediafirst.spark.performance.reports

import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.sql.streaming.{OutputMode, Trigger}


object ReadingKafkaFunctions {

  def readFromKafka(ss: SparkSession)(implicit config: Config): DataFrame = {
    val df = ss.readStream.format("kafka").
      option("kafka.bootstrap.servers", config.getString("input.logs.brokersConnectionString")).
      option("subscribe", config.getString("input.logs.topic")).
      load().selectExpr("CAST(value as String) as value")
    df
  }

  def publishLogsToKafka(df: Dataset[String], topicName: String, ss: SparkSession)(implicit config: Config) = {
    val newSparkSession = ss.newSession()
    val pubtoKafka = df
      .selectExpr("CAST(value as STRING) as value")
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", config.getString("input.logs.brokersConnectionString"))
      .option("topic", topicName)
      .option("checkpointLocation", "s3a://checkpointkafka/")
      .outputMode(OutputMode.Update())
      .trigger(Trigger.ProcessingTime("60 seconds"))
      .start().awaitTermination()

  }
}
