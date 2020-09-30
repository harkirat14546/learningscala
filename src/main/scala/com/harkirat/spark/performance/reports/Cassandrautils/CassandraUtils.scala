package com.harkirat.spark.performance.reports.Cassandrautils


import com.datastax.driver.core.Row
import com.datastax.spark.connector.cql.CassandraConnector
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

trait CassandraUtilsApi {

  def getOldestCassandraTableTimeByKeyspacePrefix(config: SparkConf, keyspacePrefix: String, table: String): Option[DateTime]

}

/** Helper class to interact with Cassandra cluster for Spark applications
 *
 * @author eforjul
 */
object CassandraUtils extends CassandraUtilsApi {

  def getCassandraConnector(config: Config, sc: SparkContext): CassandraConnector = {
    val host     = config.getString("host")
    val username = config.getString("username")
    val password = config.getString("password")
    val sparkConf = sc.getConf
      .set("spark.cassandra.connection.host", host)
      .set("spark.cassandra.auth.username", username)
      .set("spark.cassandra.auth.password", password)
    if (config.hasPath("consistencyLevel")) {
      CassandraConnector(sparkConf.set("spark.cassandra.input.consistency.level", config.getString("consistencyLevel")))
    } else {
      CassandraConnector(sparkConf)
    }
  }

  //TODO scaladoc
  def getKeyspaces(config: SparkConf): List[String] = {
    CassandraConnector(config).withSessionDo { session =>
      session.getCluster.getMetadata.getKeyspaces.map(keyspace => keyspace.getName).toList
    }
  }

  //TODO scaladoc
  def getKeyspacesStartingWith(config: SparkConf, prefix: String): List[String] = {
    getKeyspaces(config).flatMap(keyspace => if (keyspace.startsWith(prefix)) List(keyspace) else List())
  }

  /** Check if a keyspace exists on a Cassandra cluster
   *
   * @param config The Spark application SparkConf that provides Cassandra configuration and credentials
   * @param keyspace The name of the keyspace to be checked
   * @return true if the keyspace exists, false otherwise
   */
  def keyspaceExists(config: SparkConf, keyspace: String): Boolean = {
    CassandraConnector(config).withSessionDo { session =>
      session.getCluster.getMetadata.getKeyspace(keyspace) != null
    }
  }

  /** Check if a table exists on a Cassandra cluster
   *
   * @param config The Spark application SparkConf that provides Cassandra configuration and credentials
   * @param keyspace The name of the keyspace where the table is
   * @param table The name of the table to be checked
   * @return true if the table exists, false otherwise
   */
  def tableExists(config: SparkConf, keyspace: String, table: String): Boolean = {
    if (!keyspaceExists(config, keyspace)) false
    else {
      val existTry = Try(CassandraConnector(config).withSessionDo { session => session.getCluster.getMetadata.getKeyspace(keyspace).getTable(table) != null })
      existTry match {
        case Success(exist) => exist
        case Failure(_)     => false
      }
    }
  }

  //TODO refactor this with a promise
  def waitForTableExists(config: SparkConf, keyspace: String, table: String, maxWait: Long = 10000l): Unit = {
    if (maxWait > 0 && !tableExists(config, keyspace, table)) {
      Thread.sleep(500)
      waitForTableExists(config, keyspace, table, maxWait - 500)
    }
  }

  /** Run a CQL command on a Cassandra cluster
   *
   * @param config The Spark application SparkConf that provides Cassandra configuration and credentials
   * @param command The command to be executed
   */
  def runCql(config: SparkConf, command: String, consistency: String = "QUORUM"): Unit = {
    CassandraConnector(config.set("spark.cassandra.input.consistency.level", consistency)).withSessionDo { session => session.execute(command) }
  }

  //TODO scaladoc
  def getCassandraRowsFromCql(config: SparkConf, command: String, consistency: String = "QUORUM"): List[Row] = {
    val test = CassandraConnector(config.set("spark.cassandra.input.consistency.level", consistency)).withSessionDo {
      session => session.execute(command).all()
    }
    test.toList
  }

  def getCount(config: SparkConf, keyspace: String, table: String, consistency: String = "QUORUM"): Long = {
    val cql = s"SELECT count(*) FROM $keyspace.$table;"
    val result = CassandraConnector(config.set("spark.cassandra.input.consistency.level", consistency)).withSessionDo {
      session => session.execute(cql).all()
    }
    if (result.size == 0) 0L else result(0).getLong("count")
  }

  /** For a given Cassandra keyspace and prefix, returns the timestamp of the currently most ancient available data as an Option
   *
   * @param config The Spark application SparkConf that provides Cassandra configuration and credentials
   * @param keyspacePrefix The prefix of the keyspace where the tables are
   * @param table The name of the table which availability is checked
   * @return a Option[DateTime] reflecting the oldest queriable timestamp for the given keyspace prefix and table.
   */
  def getOldestCassandraTableTimeByKeyspacePrefix(config: SparkConf, keyspacePrefix: String, table: String): Option[DateTime] = {

    System.out.println("[DEBUG] checking oldest Cassandra keyspace for " + keyspacePrefix + " " + table)
    val currentTime = DateTime.now()
    val keyspace = keyspacePrefix + TimeUtils.getTableSuffix(currentTime)
    if (!tableExists(config, keyspace, table)) None
    else Some(getOldestCassandraTableTimeByKeyspacePrefixFrom(config, keyspacePrefix, table, currentTime))
  }

  /** For a given Cassandra keyspace and prefix and an intiial timestamp, returns the timestamp of the oldest available data
   *
   * @param config The Spark application SparkConf that provides Cassandra configuration and credentials
   * @param keyspacePrefix The prefix of the keyspace where the tables are
   * @param table The name of the table which availability is checked
   * @param from The time from which availability is checked
   * @return A DateTime reflecting the oldest time for which data is queriable
   */
  private def getOldestCassandraTableTimeByKeyspacePrefixFrom(config: SparkConf, keyspacePrefix: String, table: String, from: DateTime): DateTime = {
    val keyspace = keyspacePrefix + TimeUtils.getTableSuffix(from)
    if (!tableExists(config, keyspace, table)) {
      from.plusDays(1).withMillisOfDay(0)
    } else {
      getOldestCassandraTableTimeByKeyspacePrefixFrom(config, keyspacePrefix, table, from.minusDays(1))
    }
  }



}

