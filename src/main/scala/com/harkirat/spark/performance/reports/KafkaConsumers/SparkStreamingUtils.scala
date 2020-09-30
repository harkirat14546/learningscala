package com.harkirat.spark.performance.reports.KafkaConsumers


import com.ericsson.mediafirst.data.providers.kafka.KafkaClusterUtils
import com.ericsson.mediafirst.data.providers.kafka.KafkaClusterUtils.kafkaTopicsExist
import com.ericsson.mediafirst.data.providers.kafka.spark.{DataStream, SparkStreamingOffsetManager}
import com.ericsson.mediafirst.data.transformations.spark.TransformRDD
import com.ericsson.mediafirst.utils.logging.SparkLogger
import com.ericsson.mediafirst.utils.serialization.DeserializerUtils
import com.ericsson.mediafirst.utils.tools.DataDef.DataMap
import com.ericsson.mediafirst.utils.tools.TimeExtractor
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, KafkaUtils}

import scala.collection.JavaConversions._
import scala.util.Try


object SparkStreamingUtils {

  def getDirectDataStream(streamName: String, kafkaConfig: Config, ssc: StreamingContext): (DataStream, SparkStreamingOffsetManager) = {

    val debug = if (kafkaConfig hasPath "debug") kafkaConfig.getBoolean("debug") else false
    val timeExtractor = if (kafkaConfig hasPath "timeField") {
      val timeField = kafkaConfig.getString("timeField")
      if (kafkaConfig hasPath "timeFormat") Some(TimeExtractor(timeField, kafkaConfig.getString("timeFormat"))) else Some(TimeExtractor(timeField))
    } else {
      None
    }

    val (rawStream, offsetManager) = getDirectStream(kafkaConfig, ssc)

    val stream = if (kafkaConfig hasPath "transformations") {
      val transformations = kafkaConfig.getStringList("transformations")
      val deserializerSchemaList = if (kafkaConfig hasPath "deserialize") Some(DeserializerUtils.getDeserializerSchemaListFromConfig(kafkaConfig)) else None
      transformations.foldLeft(rawStream)(op = (thisStream, transformation) => {
        val params: Option[DataMap] = if (transformation == "MapParseJsonAtField") {
          Some(Map(
            "dataField" -> Try {
              kafkaConfig.getString("transformationDataField")
            }.getOrElse(None)))
        } else if (transformation == "MapParseGrokAtField") {
          Some(Map(
            "dataField" -> Try {
              kafkaConfig.getString("transformationDataField")
            }.getOrElse(None),
            "grokPattern" -> Try {
              kafkaConfig.getString("transformationGrok")
            }.getOrElse(None)))
        } else None
        TransformRDD.applyTransformationDStream(transformation, thisStream, deserializerSchemaList, timeExtractor, params)
      })
    } else {
      rawStream
    }

    val destination = if (kafkaConfig hasPath "destination") Some(kafkaConfig.getString("destination")) else None

    (DataStream(streamName, stream, timeExtractor, destination, debug), offsetManager)
  }

  def getDirectDataStreamList(config: Config, ssc: StreamingContext): List[(DataStream, SparkStreamingOffsetManager)] = {
    val streamNameList = config.getObject("stream").keySet().toList.filter(streamName => config.getBoolean(s"stream.$streamName.enable"))
    streamNameList.map { streamName =>
      val kafkaConfig = config.getConfig(s"stream.$streamName")
      getDirectDataStream(streamName, kafkaConfig, ssc)
    }
  }

  /**
   * shows if all the available topics exist
   * @param kafkaTopics all topic names
   * @param connectionString Kafka broker connection string
   * @return
   */
  def exitIfKafkaTopicIsMissing(kafkaTopics: List[String], connectionString: String): Unit = {
    val topicsDoNotExistMap = kafkaTopicsExist(kafkaTopics,connectionString).filter(_._2 == false)

    if (topicsDoNotExistMap.nonEmpty) {
      val missingTopicList = topicsDoNotExistMap.keys.toList
      SparkLogger.tsg(s"Required kafka topics ${missingTopicList.mkString(",")} are missing, exiting. Make sure kafka topics are created by the offline worker", "NA")
      SparkLogger.error(s"Required kafka topics ${missingTopicList.mkString(",")} are missing, exiting.")
    }
  }

  /**
   * shows if all the available topics exist
   * @param kafkaTopic topic name
   * @param connectionString Kafka broker connection string
   */
  def exitIfKafkaTopicIsMissing(kafkaTopic: String, connectionString: String): Unit = {
    exitIfKafkaTopicIsMissing(List(kafkaTopic), connectionString)
  }

  /**
   * Get the Spark direct stream with the offsets maintained by Stream offset manager
   * @param kafkaConfig
   * @param ssc
   * @return
   */
  def getDirectStream(kafkaConfig: Config, ssc: StreamingContext): (DStream[DataMap], SparkStreamingOffsetManager) = {
    //Get topic partition information
    val topicList = Try(kafkaConfig.getStringList("topics").toList).getOrElse(List(kafkaConfig.getString("topic"))).asInstanceOf[List[String]]

    val connectionString = kafkaConfig.getString("brokersConnectionString")
    val groupId = kafkaConfig.getString("groupId")
    // The reason to have this consumer group Id different from offset handle group id is because the Spark stream will break when using the same group id to commit offsets.
    val consumerGroupId = groupId + "-consumer"
    SparkLogger.log(s"Connect to Kafka brokers: $connectionString, topics: $topicList, groupId: $groupId")
    val kafkaParams = KafkaClusterUtils.getKafkaConsumerParameters(connectionString, consumerGroupId)

    //Retrieve the start offset for each partition
    val offsetManager = new SparkStreamingOffsetManager(kafkaConfig)

    val fromOffsets: Map[TopicPartition, Long] = kafkaConfig.getString("offsetStrategy") match {
      case "largestOnAbsent" => offsetManager.getFromOffsets(topicList, "largest")
      case "smallestOnAbsent" => offsetManager.getFromOffsets(topicList, "smallest")
      case "largestForced" => offsetManager.getFromOffsets(topicList, "largest", true)
      case "smallestForced" => offsetManager.getFromOffsets(topicList, "smallest", true)
      case other => offsetManager.getFromOffsets(topicList, "largest")
    }

    val deserializer = kafkaConfig.getString("deserializer")
    var i = 0
    val stream = deserializer match {
      case "PayloadJsonDeserializer" =>
        val topicList = fromOffsets.keys.map(topicPartition=> topicPartition.topic()).toList
        kafkaParams.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "com.ericsson.mediafirst.data.providers.kafka.decoders.PayloadJsonDeserializer")
        KafkaUtils.createDirectStream[String, DataMap](ssc, PreferConsistent, Subscribe[String, DataMap](topicList, kafkaParams, fromOffsets)).transform { rdd =>
          val ranges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
          var count = 0L
          ranges.foreach(range => {
            count += range.untilOffset - range.fromOffset
          })
          SparkLogger.debug("Got an KafkaRDD Range --------------------------- " + i + "(" + count + ")")
          i += 1

          offsetManager.append(ranges)
          rdd.map(item => item.value())
        }
      case "JsonStringDeserializer" =>
        val topicList = fromOffsets.keys.map(topicPartition=> topicPartition.topic()).toList
        kafkaParams.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "com.ericsson.mediafirst.data.providers.kafka.decoders.JsonStringDeserializer")
        KafkaUtils.createDirectStream[String, DataMap](ssc, PreferConsistent, Subscribe[String, DataMap](topicList, kafkaParams, fromOffsets)).transform { rdd =>
          val ranges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
          var count = 0L
          ranges.foreach(range => {
            count += range.untilOffset - range.fromOffset
          })
          SparkLogger.debug("Got an KafkaRDD Range --------------------------- " + i + "(" + count + ")")
          i += 1

          offsetManager.append(ranges)
          rdd.map(item => item.value())
        }
      case other => throw new UnsupportedOperationException(s"invalid deserializer: $deserializer")
    }

    (stream, offsetManager)
  }


}
