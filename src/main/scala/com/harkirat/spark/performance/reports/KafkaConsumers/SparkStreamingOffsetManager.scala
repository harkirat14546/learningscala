package com.harkirat.spark.performance.reports.KafkaConsumers


import com.ericsson.mediafirst.data.providers.kafka.spark.api.SparkKafkaProviderUtilsFunctions
import com.ericsson.mediafirst.data.providers.kafka.{KafkaClusterUtils, ProducerUtils}
import com.ericsson.mediafirst.utils.logging.SparkLogger
import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.kafka010.OffsetRange
import com.typesafe.config.{Config, ConfigValueFactory}

import scala.collection.mutable.ListBuffer

/**
 * Created by exeicen on 6/26/16. modified by epenwei 7/14/17
 */
class SparkStreamingOffsetManager(kafkaConfig: Config) {

  private val offsetBuffer = ListBuffer[(Long, Long, Array[OffsetRange])]()
  private var stepSize = 1
  private var largestTransactionId = -1L
  private var enabled = true

  def getFromOffsets(topics: Seq[String], resetStrategy: String = "largest", isForceReset: Boolean = false): Map[TopicPartition, Long] = {

    val storedOffsetMap:Map[TopicPartition, Long]= KafkaClusterUtils.fetchOffsets(kafkaConfig, topics.toList, SparkKafkaProviderUtilsFunctions.getDebugLogger(kafkaConfig))

    SparkLogger.trace("================== Stored Offset Info: ==================")
    storedOffsetMap.foreach(pair => SparkLogger.trace(pair.toString()))

    //get the offset Range
    val offsetRangeMap: Map[TopicPartition,(Long,Long)] = KafkaClusterUtils.getTopicsOffsetRanges(kafkaConfig, topics.toList, SparkKafkaProviderUtilsFunctions.getDebugLogger(kafkaConfig))
    SparkLogger.trace("================== Offset Range Info: ==================")
    offsetRangeMap.foreach(pair => SparkLogger.trace(pair.toString()))

    val combinedOffsetMap = storedOffsetMap.map {
      case (topicAndPartition, storedOffset) => {
        if (storedOffset == -1L || isForceReset) {
          val offset = if (resetStrategy equalsIgnoreCase "smallest") offsetRangeMap(topicAndPartition)._1 else offsetRangeMap(topicAndPartition)._2
          topicAndPartition -> offset
        } else {
          topicAndPartition -> storedOffset
        }
      }
    }
    SparkLogger.trace("================== Combined Offset Info: ==================")
    combinedOffsetMap.foreach(pair => SparkLogger.trace(pair.toString()))

    combinedOffsetMap
  }

  def append(ranges: Array[OffsetRange]) = {
    if(offsetBuffer.size == 0) {
      offsetBuffer.append((0, 0, ranges))
    } else {
      var count = 0L
      ranges.foreach(range => count += (range.untilOffset - range.fromOffset))

      val lastElem = offsetBuffer(offsetBuffer.length - 1)
      if(count == 0) {
        offsetBuffer.update(offsetBuffer.length - 1, (lastElem._1, lastElem._2 + 1, lastElem._3))
      } else {
        offsetBuffer.append((lastElem._2 + 1, lastElem._2 + 1, ranges))
      }
    }

    if(enabled) {
      offsetBuffer.foreach(triple => SparkLogger.debug(s"Kafka RDD count: [${triple._1} -> ${triple._2}]"))
    } else {
      offsetBuffer.clear()
    }
  }

  def setStep(size: Int): Unit = {
    stepSize = size
  }

  /**
   * Commit the offset to Kafka.
   * At the end of commit action, the top entry of the offsetBuffer is the offset that just committed.
   * @param rddId: denotes the count id of the rdd that its until offset should be commited. start from 0.
   * @return
   */

  def commit(rddId: Long): Boolean = {
    SparkLogger.debug(s"Committing kafka offset with RDD id=$rddId")
    if(!enabled)  {
      true
    } else if(offsetBuffer isEmpty) {
      SparkLogger.debug("Should not be here! Too early to commit.")
      false
    } else {
      if (rddId <= largestTransactionId) {
        SparkLogger.debug("............. Already Commit!")
        true
      } else {
        val stepFrom = (largestTransactionId + 1) * stepSize - 1
        val stepTo = (rddId + 1) * stepSize - 1

        var idx = 0
        while(idx < offsetBuffer.size && offsetBuffer(idx)._2 < stepTo) {
          idx += 1
        }

        SparkLogger.debug(s"processed rdd [$stepFrom - $stepTo], commiting ${offsetBuffer(idx)}")

        if(idx >= offsetBuffer.size) {
          SparkLogger.debug("Something wrong with your transactionId increment.")
          false
        } else {

          if(offsetBuffer(idx)._1 <= stepFrom) {
            SparkLogger.debug("............. Commit Already Succeed!")
            true
          } else {

            val offsetRanges = offsetBuffer(idx)._3
            val offsetMap = offsetRanges.map(range => range.topicPartition() -> range.untilOffset).toMap
            KafkaClusterUtils.commitOffsets(kafkaConfig, offsetMap, SparkKafkaProviderUtilsFunctions.getDebugLogger(kafkaConfig))
            SparkLogger.debug(" ............ Commit Succeed!")
            // Only leave the commit succeed branch here, as the commit failure will trigger the job exit.
            largestTransactionId = rddId
            offsetBuffer.trimStart(idx)
            true
          }
        }
      }
    }
  }

  def disable(): Unit = {
    enabled = false
  }

  def enable(): Unit = {
    enabled = true
  }
}

