package com.mediakind.mediafirst.spark.performance.reports

import org.apache.spark.{SparkConf, TaskContext}
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.{Minutes, Seconds}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.dstream.DStream
import scala.io._
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010._
import org.apache.log4j.Logger
import com.ericsson.mediafirst.data.providers.kafka
object SparkStreamingOffsetHandling extends App {

  val topics=Array("elasticsearchtopicperfs")
  val sparkConf=new SparkConf().setAppName("offset").setMaster("local[*]")
  val ssc=new StreamingContext(sparkConf,Seconds(60))
  val sc=ssc.sparkContext
  sc.setLogLevel("INFO")
  val preferredHosts = LocationStrategies.PreferConsistent
  val fromOffsets=Map(new TopicPartition("elasticsearchtopicperfs",0)->2L)
  val kafkaParams = Map[String, Object](
    "bootstrap.servers" -> "localhost:9092",
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[StringDeserializer],
    "group.id" -> "use_a_separate_group_id_for_each_stream",
   // "auto.offset.reset" -> "latest",
    "enable.auto.commit" -> (false: java.lang.Boolean)
  )
  val messages=KafkaUtils.createDirectStream[String,String](
    ssc,preferredHosts,ConsumerStrategies.Subscribe[String,String](topics,kafkaParams,fromOffsets)
  )

val line=messages.map(x=> (x.key(),x.offset(),x.topic(),x.partition(),x.value()))
  messages.foreachRDD{ rdd=>
    val offsetRanges=rdd.asInstanceOf[HasOffsetRanges].offsetRanges
    rdd.foreachPartition{ iter =>
      val o:OffsetRange=offsetRanges(TaskContext.get.partitionId())
      println(s"${o.topic},${o.partition},${o.fromOffset},${o.untilOffset}")
    }
  }
  line.print()
  ssc.start()
  ssc.awaitTermination()
}
