//package com.harkirat.spark.performance.reports.KafkaConsumers
//
//import java.io.{PrintWriter, StringWriter}
//import java.util
//
//import com.ericsson.mediafirst.utils.tools.RetryUtils
//import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
//import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer, OffsetAndMetadata, OffsetCommitCallback}
//import org.apache.kafka.common.PartitionInfo
//import org.apache.kafka.clients.consumer.internals.ConsumerCoordinator
//import org.apache.kafka.clients.producer.ProducerConfig
//import org.apache.kafka.common.TopicPartition
//
//import scala.collection.JavaConversions._
//import scala.util.Try
//
//
///**
// * Utility object to get information about a Kafka Cluster
// *
// * @author eforjul,epenwei
// *
// */
//object KafkaClusterUtils {
//
//  /**
//   * Default number of retries attempts for each request sent to Zookeeper
//   */
//  final val DEFAULT_RETRIES_ATTEMPTS = 10
//  /**
//   * Default delay in seconds between each request attempt sent to Zookeeper
//   */
//  final val DEFAULT_RETRIES_DELAY = 10
//  /**
//   * Default logger function to report failed request attempts sent to Zookeeper
//   */
//  final val DEFAULT_LOGGER = (s: String, error: Boolean) => if (error) System.err.println(s) else println(s)
//  /**
//   * Default group Id constant
//   */
//  final val DEFAULT_GROUP_ID = "defaultGroupId"
//  /**
//   * Get list of all available topics on the Kafka cluster
//   *
//   * @param kafkaConfig brokersConnectionString: Kafka broker addresses
//   * @param logger logger function used to report failed Zookeeper connection and requests attempts
//   * @return a set of available topics
//   */
//  def getAvailableTopics(kafkaConfig: Config, logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Set[String] = {
//    val brokersConnectionString = kafkaConfig.getString("brokersConnectionString")
//    // Give default group Id as the group Id is not mandatory for getting all topics
//    val groupId = if(kafkaConfig.hasPath("groupId")) kafkaConfig.getString("groupId") else DEFAULT_GROUP_ID
//    val retriesAttempts = Try(kafkaConfig.getInt("retries.attempts")).getOrElse(DEFAULT_RETRIES_ATTEMPTS)
//    val retryDelay = Try(kafkaConfig.getInt("retries.delay")).getOrElse(DEFAULT_RETRIES_DELAY)
//    RetryUtils.retryOrDie(retriesAttempts, retryDelay = retryDelay * 1000,
//      loopFn = {
//        logger("Could not get available topic list from Kafka brokers, retrying...", true)
//      },
//      failureFn = {
//        logger("Could not get available topic list from Kafka brokers, giving up...", true)
//      },
//      throwableParserFn = getThrowableLoggerFn(logger)
//    )(getAvailableTopicsOnce(brokersConnectionString, groupId))
//  }
//
//  /**
//   * Get list of all available topics on the Kafka cluster without retry
//   * @param brokersConnectionString brokersConnectionString: Kafka broker addresses
//   * @return a set of available topics
//   */
//  def getAvailableTopicsOnce(brokersConnectionString: String, groupId: String):Set[String]= {
//    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
//    val topics = offsetManager.listTopics().keySet()
//    offsetManager.close()
//    topics.toSet
//  }
//
//  /**
//   * Get the default Kafka consumer parameters for creating Kafka consumer.
//   * @param brokersConnectionString the broker connection string
//   * @param groupId the group Id
//   * @return the default Kafka parameters
//   */
//  def getKafkaConsumerParameters(brokersConnectionString: String, groupId: String): util.Map[String, Object]={
//    val kafkaParameters = new util.HashMap[String, Object]()
//    kafkaParameters.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
//    kafkaParameters.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none")
//    kafkaParameters.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokersConnectionString)
//    kafkaParameters.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
//    kafkaParameters.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "100000")
//    kafkaParameters.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
//    kafkaParameters.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
//    kafkaParameters
//  }
//
//  /**
//   * Get the default Kafka producer parameters for creating Kafka producer.
//   * @param brokersConnectionString the broker connection string
//   * @return the default Kafka parameters
//   */
//  def getKafkaProducerParameters(brokersConnectionString: String): util.Map[String, Object]={
//    val kafkaParameters = new util.HashMap[String, Object]()
//    kafkaParameters.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokersConnectionString)
//    kafkaParameters.put(ProducerConfig.BATCH_SIZE_CONFIG, "256000")
//    kafkaParameters.put(ProducerConfig.LINGER_MS_CONFIG, "200")
//    // Comment this to disable the message compression
//    //kafkaParameters.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
//    //kafkaParameters.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "100000000")
//    kafkaParameters.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
//    kafkaParameters.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
//    kafkaParameters
//  }
//
//  /**
//   * Check if an existing topics on Kafka is empty where empty means that no data is available on Kafka (not that the offsets are equal to 0)
//   *
//   * @param config configuration for the Kafka connection, such as brokersConnectionString
//   * @param topic the name of the topics to be checked
//   * @param logger optional custom logger function to be used
//   * @return true if the topics is empty (i.e. if for each partition, the minimum offset is equal to the maximum offset) false data is available or if the
//   *         topics does not exist at all
//   */
//  def topicIsEmpty(config: Config, topic: String, logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Boolean = {
//    val offsetRanges = getTopicOffsetRanges(config, topic, logger)
//    if (offsetRanges.isEmpty) false else offsetRanges.forall { case (partitionId, (minOffset, maxOffset)) => minOffset == maxOffset}
//  }
//
//  /**
//   * Get offset ranges for given Kafka topics
//   *
//   * @param config configuration for the Kafka connection, such as brokersConnectionString
//   * @param topics the name of the topics whose offset ranges are to be returned
//   * @param logger optional custom logger function to be used
//   * @return An Either error string or a Map containing Kafka partition -> (earliestoffset, latestoffset) mapping
//   */
//  def getTopicsOffsetRanges(config: Config, topics: List[String], logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Map[TopicPartition, (Long, Long)] = {
//    val brokersConnectionString = config.getString("brokersConnectionString")
//    val groupId = config.getString("groupId")
//    logger(s"Prepare to get offset range for topics: $topics, groupId: $groupId", false)
//    val retriesAttempts = Try(config.getInt("retries.attempts")).getOrElse(DEFAULT_RETRIES_ATTEMPTS)
//    val retryDelay = Try(config.getInt("retries.delay")).getOrElse(DEFAULT_RETRIES_DELAY)
//    val offsetRanges = RetryUtils.retryOrDie(retriesAttempts, retryDelay = retryDelay * 1000,
//      loopFn = {
//        logger(s"Could not get offset range for topics ${topics}, retrying...", true)
//      },
//      failureFn = {
//        logger(s"Could not get offset range for topics ${topics}, giving up...", true)
//      },
//      throwableParserFn = getThrowableLoggerFn(logger)
//    )(getTopicsOffsetRangesOnce(brokersConnectionString, groupId, topics, logger))
//    logger(s"The Topic $topics offset ranges are ${offsetRanges.toList}", false)
//    offsetRanges
//  }
//
//  /**
//   * Get topic offset ranges withough retry
//   * @param brokersConnectionString the Kafka broker address
//   * @param topics the topic list
//   * @return the topic partition and corresponding offsets
//   */
//  def getTopicsOffsetRangesOnce(brokersConnectionString: String, groupId: String, topics: List[String], logger: (String, Boolean) => Unit) : Map[TopicPartition, (Long, Long)] ={
//    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
//    val partitions = getPartitions(offsetManager, topics, logger).map {
//      partitionInfo: PartitionInfo => new TopicPartition(partitionInfo.topic(), partitionInfo.partition())
//    }
//    val beginOffsets = offsetManager.beginningOffsets(partitions)
//    val endOffsets = offsetManager.endOffsets(partitions)
//    offsetManager.close()
//    val offsetRanges = partitions.map(topicPartition => topicPartition -> (beginOffsets.get(topicPartition).toLong, endOffsets.get(topicPartition).toLong)).toMap
//    if (offsetRanges.size != partitions.size) throw new RuntimeException(s"Fetched offsets ranges are not complete, offset ranges: ${offsetRanges}, partitions: ${partitions}")
//    offsetRanges
//  }
//
//  /**
//   * Get all the partitions for a topic
//   * @param kafkaConsumer
//   * @param topics
//   * @return
//   */
//  def getPartitions(kafkaConsumer: KafkaConsumer[String, String], topics: List[String], logger: (String, Boolean) => Unit):List[PartitionInfo] = {
//    topics.map { topic =>
//      val partitions = kafkaConsumer.partitionsFor(topic).toList
//      if (partitions.map(p => p.partition()).max + 1 != partitions.size) {
//        val existingPartitons = partitions.map(p => p.partition())
//        val errorMsg = s"Fail to get partitions for topic: $topic, some partition missing, total partition number: ${partitions.size}, existing partitions: $existingPartitons"
//        logger(errorMsg, true)
//        throw new RuntimeException(errorMsg)
//      } else partitions
//    }.flatten
//  }
//
//  /**
//   * Get offset ranges for a single Kafka topic
//   * @param config configuration for the Kafka connection, such as brokersConnectionString
//   * @param topic the name of the topics whose offset ranges are to be returned
//   * @param logger optional custom logger function to be used
//   * @return an option for a map containing the offset ranges. If no brokers could be found for the topics partitions, None is returned, otherwise, a Map whose
//   *         keys are the partition ids and values are (minOffset, maxOffset) tuples is returned
//   */
//  def getTopicOffsetRanges(config: Config, topic: String, logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Map[Int, (Long, Long)] = {
//    val partitionOffsets = getTopicsOffsetRanges(config, List(topic), logger)
//    for ((k, v) <- partitionOffsets) yield (k.partition() -> v)
//  }
//
//  /**
//   * shows if all the available topics exist
//   * @param kafkaTopics all topics names
//   * @param brokerConnectionString zookeeper connection
//   * @return a map from topics name to kafka existence flag
//   */
//  def kafkaTopicsExist(kafkaTopics: List[String], brokerConnectionString: String, logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Map[String,Boolean] = {
//    val kafkaConfig = ConfigFactory.empty().withValue("brokersConnectionString", ConfigValueFactory.fromAnyRef(brokerConnectionString))
//    val availableTopics = KafkaClusterUtils.getAvailableTopics(kafkaConfig, logger)
//    kafkaTopics.map(topic => (topic,availableTopics.contains(topic))).toMap
//  }
//
//  /**
//   * Fetch the stored offset from Kafka
//   * @param config configuration for the Kafka connection, such as brokersConnectionString, debug etc
//   * @param topic the name of the topics whose offset ranges are to be returned
//   * @param logger optional custom logger function to be used
//   * @return the stored offsets in Kafka
//   */
//  def fetchOffsets(config: Config, topic: String, logger: (String, Boolean) => Unit = DEFAULT_LOGGER):Map[Int, Long] = {
//    val topicOffsets = fetchOffsets(config, List(topic), logger)
//    for ((k, v) <- topicOffsets) yield (k.partition() -> v)
//  }
//
//  /**
//   * Fetch the stored offset from Kafka
//   * @param config configuration for the Kafka connection, such as brokersConnectionString, debug etc
//   * @param topics the name of the topics whose offset ranges are to be returned
//   * @return the stored offsets in Kafka
//   */
//  def fetchOffsets(config: Config, topics: List[String], logger: (String, Boolean) => Unit): Map[TopicPartition, Long] = {
//    val brokersConnectionString = config.getString("brokersConnectionString")
//    val groupId = config.getString("groupId")
//    logger(s"Prepare to fetch stored offsets for topic: $topics, groupId: $groupId", false)
//    if (topics.isEmpty) throw new IllegalArgumentException("Topic is needed to fetch the topic offsets")
//    val retriesAttempts = Try(config.getInt("retries.attempts")).getOrElse(DEFAULT_RETRIES_ATTEMPTS)
//    val retryDelay = Try(config.getInt("retries.delay")).getOrElse(DEFAULT_RETRIES_DELAY)
//    val offsets =  RetryUtils.retryOrDie(retriesAttempts, retryDelay = retryDelay * 1000,
//      loopFn = {
//        logger(s"Could not fetech stored offsets for topics: $topics, retrying...", true)
//      },
//      failureFn = {
//        logger(s"Could not fetech stored offsets for topics: $topics, giving up...", true)
//      },
//      throwableParserFn = getThrowableLoggerFn(logger)
//    )(fetchOffsetsOnce(brokersConnectionString, groupId, topics, logger))
//    logger(s"Fetched stored offsets for topics $topics are $offsets", false)
//    offsets
//  }
//
//  /**
//   * Fetch stored offsets from Kafka without retry
//   * @param brokersConnectionString the Kafka connection string
//   * @param topics the topic list
//   * @return the topic partitions and the offsets
//   */
//  def fetchOffsetsOnce(brokersConnectionString: String, groupId: String, topics: List[String], logger: (String, Boolean) => Unit):Map[TopicPartition, Long]= {
//    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
//    val partitions = getPartitions(offsetManager, topics, logger)
//    val topicPartitions = partitions.map(partitionInfo =>new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
//    logger(s"Constructed topic partitions.$topics, $topicPartitions", false)
//    val offsets = batchFetchOffset(offsetManager, topicPartitions.toSet).toMap
//    offsetManager.close()
//    if (offsets.size != partitions.size) throw new RuntimeException(s"Fetched offsets are not complete, offsets: ${offsets}, partitions: ${partitions}")
//    offsets
//  }
//
//  /**
//   * Internal codes to fetch the offsets, this is to resolve the Kafka limitation to do batch fetching
//   * @param kafkaConsumer the KafkaConsumer which to do the batch fetching
//   * @param topicsPartitions the topic partitions to fetch offsets
//   * @return the offsets according to partitions
//   */
//  private def batchFetchOffset(kafkaConsumer: KafkaConsumer[String, String], topicsPartitions: Set[TopicPartition]) = {
//    val f = kafkaConsumer.getClass.getDeclaredField("coordinator") //NoSuchFieldException
//    f.setAccessible(true)
//    val coordinator = f.get(kafkaConsumer).asInstanceOf[ConsumerCoordinator]
//    val offsets = coordinator.fetchCommittedOffsets(topicsPartitions)
//    val partitionEarliestOffsets = if (offsets.size() != topicsPartitions.size) {
//      (for((k,v)<-kafkaConsumer.beginningOffsets(topicsPartitions)) yield k->v.toLong).toMap
//    } else Map[TopicPartition, Long]()
//    for(topicPartition <-topicsPartitions)
//      yield if (offsets.containsKey(topicPartition))  {
//        (topicPartition -> offsets(topicPartition).offset())
//      } else {
//        // If there is no stored offsets, use the earliest offset by default
//        // TBD choose the offset based on the options
//        (topicPartition -> partitionEarliestOffsets(topicPartition))
//      }
//
//  }
//
//  /**
//   * Commit stored offset for single topic to Kafka
//   * @param config configuration for the Kafka connection, such as brokersConnectionString, debug etc
//   * @param offsets the offsets to commit to Kafka
//   * @param committedTopic the topic which the offsets related to
//   * @param logger the logger function
//   * @return the commit error or Unit if succeed
//   */
//  def commitOffsets(config: Config, offsets: Map[Int, Long], committedTopic: String = "", logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Unit  = {
//    val topic = if (committedTopic.equals("")) config.getString("topic") else committedTopic
//    val topicPartitionOffsets = for ((partition, offset) <- offsets) yield new TopicPartition(committedTopic, partition) -> offset
//    commitOffsets(config, topicPartitionOffsets, logger)
//  }
//
//  /**
//   * Commit the stored offset for multiple topics to Kafka
//   * @param config configuration for the Kafka connection, such as brokersConnectionString, debug etc
//   * @param offsets the offsets to commit to Kafka
//   * @return the commit error or Unit if succeed
//   */
//  def commitOffsets(config: Config, offsets: Map[TopicPartition, Long], logger: (String, Boolean) => Unit): Unit = {
//    val brokersConnectionString = config.getString("brokersConnectionString")
//    val groupId = config.getString("groupId")
//    logger(s"Prepare to commit offsets: $offsets, groupId: $groupId", false)
//    val retriesAttempts = Try(config.getInt("retries.attempts")).getOrElse(DEFAULT_RETRIES_ATTEMPTS)
//    val retryDelay = Try(config.getInt("retries.delay")).getOrElse(DEFAULT_RETRIES_DELAY)
//    RetryUtils.retryOrDie(retriesAttempts, retryDelay = retryDelay * 1000,
//      loopFn = {
//        logger("Could not commit offsets to Kafka brokers, retrying...", true)
//      },
//      failureFn = {
//        logger("Could not commit offsets to Kafka brokers, giving up...", true)
//      },
//      throwableParserFn = getThrowableLoggerFn(logger)
//    )(commitOffsetsOnce(brokersConnectionString, groupId, offsets))
//
//    logger("Offsets have been committed.", false)
//
//  }
//
//  /**
//   * Commit the topic offset once without retry
//   * @param brokersConnectionString the broker connection string
//   * @param offsets the offsets
//   */
//  def commitOffsetsOnce(brokersConnectionString: String, groupId: String, offsets: Map[TopicPartition, Long]): Unit = {
//    val offsetManager = getOffsetManager(brokersConnectionString, groupId)
//    val offsetsMap = offsets.map { case (topicPartiton, offset) => topicPartiton -> new OffsetAndMetadata(offset, "") }
//    offsetManager.commitSync(offsetsMap)
//    offsetManager.close()
//  }
//
//  /**
//   * Reset the stored offset in Kafka
//   * @param config configuration for the Kafka connection, such as brokersConnectionString, debug etc
//   * @param resetTopic the topic which the offsets related to
//   * @return the error or Unit if succeed
//   */
//  def resetOffsets(config: Config, resetTopic: String = "", logger: (String, Boolean) => Unit = DEFAULT_LOGGER): Unit = {
//    val brokersConnectionString = config.getString("brokersConnectionString")
//    val topic = if (resetTopic.equals("")) config.getString("topic") else resetTopic
//    logger(s"Start to reset offsets for topic: $topic", false)
//    val partitionCount = ProducerUtils.getTopicPartitionCount(config, topic)
//    logger(s"Total partition count is: $partitionCount", false)
//    if (partitionCount > 0) {
//      val newOffsets = ((0 to partitionCount - 1).map(i => i -> 0l)).toMap
//      commitOffsets(config, newOffsets, topic, logger)
//    } else {
//      throw new RuntimeException(s"Can not get the partition count for topic $topic to reset")
//    }
//  }
//
//  /**
//   * Internal function to return the OffsetManager(Consumer)
//   * @param brokerConnectionString the brokers connection string
//   * @return the error or the OffsetManager(KafkaConsumer)
//   */
//  private def getOffsetManager(brokerConnectionString: String, groupId: String): KafkaConsumer[String, String] = {
//    if (brokerConnectionString.isEmpty) throw new IllegalArgumentException("Brokers connection string is a mandatory input")
//    val props = getKafkaConsumerParameters(brokerConnectionString, groupId)
//    new KafkaConsumer[String, String](props)
//  }
//
//  /**
//   * Get the throwable printer based on existing logger
//   * @param logger the current logger
//   * @return the throwable printer
//   */
//  def getThrowableLoggerFn(logger: (String, Boolean) => Unit = DEFAULT_LOGGER) = {
//    def throwableLogger(e: Throwable) =
//    {
//      val sw = new StringWriter
//      // Below is for futher trouble shooting to see the error detail.
//      if (e.getCause != null) {
//        sw.write("[ERROR] The error type is:" + e.getClass.toString)
//        sw.write("[ERROR] The error root cause type is :" + e.getCause.getClass.toString)
//      } else {
//        sw.write("[ERROR] The error type is:" + e.getClass.toString)
//      }
//      e.printStackTrace(new PrintWriter(sw))
//      logger(sw.toString, true)
//    }
//    throwableLogger(_)
//  }
//
//  /**
//   * Get the condition check function
//   * @return the function of condition check
//   */
//  def isKafkaOffsetOutOfRange(e: Throwable) = {
//    e.getCause != null && e.getCause.isInstanceOf[org.apache.kafka.clients.consumer.OffsetOutOfRangeException]
//  }
//
//}
//
