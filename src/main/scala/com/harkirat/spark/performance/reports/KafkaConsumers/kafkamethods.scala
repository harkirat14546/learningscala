package com.harkirat.spark.performance.reports.KafkaConsumers

import com.ericsson.mediafirst.data.providers.kafka.KafkaClusterUtils
import java.io.{PrintWriter, StringWriter}
import java.util

import com.ericsson.mediafirst.data.providers.kafka.KafkaClusterUtils.{DEFAULT_GROUP_ID, DEFAULT_LOGGER, DEFAULT_RETRIES_ATTEMPTS, DEFAULT_RETRIES_DELAY, batchFetchOffset, fetchOffsets, fetchOffsetsOnce, getAvailableTopicsOnce, getOffsetManager, getPartitions, getThrowableLoggerFn}
import com.ericsson.mediafirst.utils.tools.RetryUtils
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer, OffsetAndMetadata, OffsetCommitCallback}
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.clients.consumer.internals.ConsumerCoordinator
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConversions._
import scala.util.Try

object kafkamethods extends App {
  val DEFAULT_LOGGER = (s: String, error: Boolean) => if (error) System.err.println(s) else println(s)
 val DEFAULT_RETRIES_ATTEMPTS = 10
  val DEFAULT_RETRIES_DELAY = 10
  val DEFAULT_GROUP_ID = "defaultGroupId"
  val re=getOffsetManager("localhost:9092","goli")
  re.subscribe(util.Arrays.asList("elasticsearchtopicperfs"))

val ze=getKafkaConsumerParameters("localhost:9092","goli")
  val aval=getAvailableTopicsOnce("localhost:9092","goli")
val partitions=getPartitions(re,List("elasticsearchtopicperfs"),DEFAULT_LOGGER)
  val partitiononce=getTopicsOffsetRangesOnce("localhost:9092","goli",
    List("elasticsearchtopicperfs"),DEFAULT_LOGGER)
  val config=ConfigFactory.load().getConfig("mediafirst")
  println(config.getString("input.logs.brokersConnectionString"))
  val gettopicspartitionsOffsetRanges=getTopicsOffsetRanges(config,List("elasticsearchtopicperfs"),DEFAULT_LOGGER)
  println(ze)
  println(aval)
  println(partitions)
  println(partitiononce)
  println(s"Yes this $gettopicspartitionsOffsetRanges")

  def fetchOffsetsOnce(brokersConnectionString: String, groupId: String, topics: List[String], logger: (String, Boolean) => Unit):Map[TopicPartition, Long]= {
    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
    val partitions = getPartitions(offsetManager, topics, logger)
    val topicPartitions = partitions.map(partitionInfo =>new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
    logger(s"Constructed topic partitions.$topics, $topicPartitions", false)
    val offsets = batchFetchOffset(offsetManager, topicPartitions.toSet).toMap
    offsetManager.close()
    if (offsets.size != partitions.size) throw new RuntimeException(s"Fetched offsets are not complete, offsets: ${offsets}, partitions: ${partitions}")
    offsets
  }




  def batchFetchOffset(kafkaConsumer: KafkaConsumer[String, String], topicsPartitions: Set[TopicPartition]) = {
    val f = kafkaConsumer.getClass.getDeclaredField("coordinator") //NoSuchFieldException
    f.setAccessible(true)
    val coordinator = f.get(kafkaConsumer).asInstanceOf[ConsumerCoordinator]
    val offsets = coordinator.fetchCommittedOffsets(topicsPartitions,10000)
    val partitionEarliestOffsets = if (offsets.size() != topicsPartitions.size) {
      (for((k,v)<-kafkaConsumer.beginningOffsets(topicsPartitions)) yield k->v.toLong).toMap
    } else Map[TopicPartition, Long]()
    for(topicPartition <-topicsPartitions)
      yield if (offsets.containsKey(topicPartition))  {
        (topicPartition -> offsets(topicPartition).offset())
      } else {
        // If there is no stored offsets, use the earliest offset by default
        // TBD choose the offset based on the options
        (topicPartition -> partitionEarliestOffsets(topicPartition))
      }
  }

  def getThrowableLoggerFn(logger: (String, Boolean) => Unit = DEFAULT_LOGGER) = {
    def throwableLogger(e: Throwable) =
    {
      val sw = new StringWriter
      // Below is for futher trouble shooting to see the error detail.
      if (e.getCause != null) {
        sw.write("[ERROR] The error type is:" + e.getClass.toString)
        sw.write("[ERROR] The error root cause type is :" + e.getCause.getClass.toString)
      } else {
        sw.write("[ERROR] The error type is:" + e.getClass.toString)
      }
      e.printStackTrace(new PrintWriter(sw))
      logger(sw.toString, true)
    }
    throwableLogger(_)
  }

  def getTopicsOffsetRanges(config: Config, topics: List[String], logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Map[TopicPartition, (Long, Long)] = {
    val brokersConnectionString = config.getString("input.logs.brokersConnectionString")
    val groupId = config.getString("input.logs.groupId ")
    logger(s"Prepare to get offset range for topics: $topics, groupId: $groupId", false)
    val retriesAttempts = Try(config.getInt("retries.attempts")).getOrElse(DEFAULT_RETRIES_ATTEMPTS)
    val retryDelay = Try(config.getInt("retries.delay")).getOrElse(DEFAULT_RETRIES_DELAY)
    val offsetRanges = RetryUtils.retryOrDie(retriesAttempts, retryDelay = retryDelay * 1000,
      loopFn = {
        logger(s"Could not get offset range for topics ${topics}, retrying...", true)
      },
      failureFn = {
        logger(s"Could not get offset range for topics ${topics}, giving up...", true)
      },
      throwableParserFn = getThrowableLoggerFn(logger)
    )(getTopicsOffsetRangesOnce(brokersConnectionString, groupId, topics, logger))
    logger(s"The Topic $topics offset ranges are ${offsetRanges.toList}", false)
    offsetRanges
  }


  def getTopicsOffsetRangesOnce(brokersConnectionString: String, groupId: String, topics: List[String], logger: (String, Boolean) => Unit) : Map[TopicPartition, (Long, Long)] ={
    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
    val partitions = getPartitions(offsetManager, topics, logger).map {
      partitionInfo: PartitionInfo => new TopicPartition(partitionInfo.topic(), partitionInfo.partition())
    }
    val beginOffsets = offsetManager.beginningOffsets(partitions)
    val endOffsets = offsetManager.endOffsets(partitions)
    offsetManager.close()
    val offsetRanges = partitions.map(topicPartition => topicPartition -> (beginOffsets.get(topicPartition).toLong, endOffsets.get(topicPartition).toLong)).toMap
    if (offsetRanges.size != partitions.size) throw new RuntimeException(s"Fetched offsets ranges are not complete, offset ranges: ${offsetRanges}, partitions: ${partitions}")
    offsetRanges
  }


  def getPartitions(kafkaConsumer: KafkaConsumer[String, String], topics: List[String], logger: (String, Boolean) => Unit):List[PartitionInfo] = {
    topics.map { topic =>
      val partitions = kafkaConsumer.partitionsFor(topic).toList
      if (partitions.map(p => p.partition()).max + 1 != partitions.size) {
        val existingPartitons = partitions.map(p => p.partition())
        val errorMsg = s"Fail to get partitions for topic: $topic, some partition missing, total partition number: ${partitions.size}, existing partitions: $existingPartitons"
        logger(errorMsg, true)
        throw new RuntimeException(errorMsg)
      } else partitions
    }.flatten
  }

  def getAvailableTopicsOnce(brokersConnectionString: String, groupId: String):Set[String]= {
    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
    val topics = offsetManager.listTopics().keySet()
    offsetManager.close()
    topics.toSet
  }

  def getAvailableTopics(kafkaConfig: Config, logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Set[String] = {
    val brokersConnectionString = kafkaConfig.getString("brokersConnectionString")
    // Give default group Id as the group Id is not mandatory for getting all topics
    val groupId = if(kafkaConfig.hasPath("groupId")) kafkaConfig.getString("groupId") else DEFAULT_GROUP_ID
    val retriesAttempts = Try(kafkaConfig.getInt("retries.attempts")).getOrElse(DEFAULT_RETRIES_ATTEMPTS)
    val retryDelay = Try(kafkaConfig.getInt("retries.delay")).getOrElse(DEFAULT_RETRIES_DELAY)
    RetryUtils.retryOrDie(retriesAttempts, retryDelay = retryDelay * 1000,
      loopFn = {
        logger("Could not get available topic list from Kafka brokers, retrying...", true)
      },
      failureFn = {
        logger("Could not get available topic list from Kafka brokers, giving up...", true)
      },
      throwableParserFn = getThrowableLoggerFn(logger)
    )(getAvailableTopicsOnce(brokersConnectionString, groupId))
  }
  def getKafkaConsumerParameters(brokersConnectionString: String, groupId: String): util.Map[String, Object]={
    val kafkaParameters = new util.HashMap[String, Object]()
    kafkaParameters.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    kafkaParameters.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none")
    kafkaParameters.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokersConnectionString)
    kafkaParameters.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    kafkaParameters.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "100000")
    kafkaParameters.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    kafkaParameters.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    kafkaParameters
  }

  def getOffsetManager(brokerConnectionString: String, groupId: String): KafkaConsumer[String, String] = {
    if (brokerConnectionString.isEmpty) throw new IllegalArgumentException("Brokers connection string is a mandatory input")
    val props = getKafkaConsumerParameters(brokerConnectionString, groupId)
    new KafkaConsumer[String, String](props)
  }


}
