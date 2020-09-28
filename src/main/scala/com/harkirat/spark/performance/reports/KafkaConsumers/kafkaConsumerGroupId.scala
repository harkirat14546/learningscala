package com.harkirat.spark.performance.reports.KafkaConsumers

import java.util
import org.apache.kafka.clients.consumer.KafkaConsumer
import  java.util.Properties
import java.io._
import scala.collection.JavaConverters._
import scala.tools.nsc.io.File
import scala.util.{Try,Success,Failure}
object kafkaConsumerGroupId {
  def main( args:Array[String]): Unit = {
    fromkafka("elasticsearchtopicperfs")
  }
  def todouble(n:String)={ scala.util.Try(n.toDouble) }
def fromkafka(topic:String)={
  val props=new Properties()
  props.put("bootstrap.servers","localhost:9092")
  props.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer")
  props.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer")
  props.put("auto.offset.reset","latest")
  props.put("group.id","goli")
  props.put("enable.auto.commit","false")
  val consumer:KafkaConsumer[String,String]=new KafkaConsumer[String,String](props)
  consumer.subscribe(util.Arrays.asList(topic))
 println( consumer.listTopics())
  val wr=File("/Users/mediakind/studymaterial/study_material/scalapractise/csvfiles/consumer.txt")
  while(true) {
    val record=consumer.poll(100).asScala

    for ( data <- record.iterator) {
      val re=todouble(data.value())
      wr.appendAll(data.value()+"\n")
      println(data.value(),data.partition(),data.offset(),data.topic(),data.timestamp())
      consumer.commitSync()
    }
  }
}
}
