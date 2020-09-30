package com.harkirat.spark.performance.reports.Cassandrautils

import com.datastax.spark.connector.CassandraRow
import org.joda.time.DateTime

import scala.util.{Success, Try}


case class SubscriberAccount(accountId: String,
                             defaultuserid: String,
                             deviceids: Set[String],
                             disabled: Boolean,
                             dvrnotificationsenabled: Boolean,
                             groups: Set[String],
                             ipaddress: String,
                             legacyaccountexternalid: String,
                             legacybranchid: String,
                             time: String,  //we do not time from Subscriber Account. This is a bug
                             pbroverride: Boolean,
                             userids: Set[String],
                             profileids:Set[String],
                             accounttype: String,
                             mediafirstRequiredSKU: String,
                             mediafirstOptionalSKUs: Set[String],
                             stateflags:Map[String,DateTime],
                             tenantId: String = "default"
                            ) {

//  def getMediafirstRequiredSKUDescription(implicit ref: SkuReference): String = ref.reference.getOrElse(mediafirstRequiredSKU, "")
//
//  def getMediafirstOptionalSKUDescriptions(implicit ref: SkuReference): Set[(String, String)] = {
//    mediafirstOptionalSKUs.map(sku => (sku, ref.reference.getOrElse(sku, "")))
//  }

  /**
   * This function abstract the channel map out from the groups cloumn
   *
   * @return
   */
  def getGroupChannelMap: (String, Option[String]) = {
    groups.find(p => p.startsWith("2|#|")) match {
      case Some(channelMaps) => (accountId, Some(channelMaps.stripPrefix("2|#|")))
      case None => (accountId, None)
    }
  }

  /**
   * Extract the channel map out from the groups column
   *
   * @return
   */
  def getUserTenantChannelMap: (String, String, Option[String]) = {
    groups.find(p => p.startsWith("2|#|")) match {
      case Some(channelMaps) => (accountId, tenantId.toLowerCase(), Some(channelMaps.stripPrefix("2|#|")))
      case None => (accountId, tenantId.toLowerCase(), None)
    }
  }
}

object SubscriberAccount {

  /**
   * reads in from the cassandra row and query the Cassandra table
   *
   * @param row inputs from Cassandra
   * @return
   */
  def createFromCassandraRow(row: CassandraRow): Option[SubscriberAccount] = {
    val defaultDateTime = new DateTime(0)
    val emptyString = "null"


    Try(new SubscriberAccount(row.getStringOption("accountid").getOrElse(emptyString),
      row.getStringOption("defaultuserid").getOrElse(emptyString),
      Try(row.getSet[String]("deviceids")).getOrElse(Set()),
      row.getBooleanOption("disabled").getOrElse(false),
      row.getBooleanOption("dvrnotificationsenabled").getOrElse(false),
      Try(row.getSet[String]("groups")).getOrElse(Set()),
      row.getStringOption("ipaddress").getOrElse(emptyString),
      row.getStringOption("legacyaccountexternalid").getOrElse(emptyString),
      row.getStringOption("legacybranchid").getOrElse(emptyString),
      defaultDateTime.toString(),
      row.getBooleanOption("pbroverride").getOrElse(false),
      Try(row.getSet[String]("userids")).getOrElse(Set()),
      Try(row.getSet[String]("profileids")).getOrElse(Set()),
      row.getStringOption("accounttype").getOrElse(emptyString),
      Try(row.getString("mediafirstrequiredsku").split(" ").mkString("-")).getOrElse( emptyString),
      Try(row.getSet[String]("mediafirstoptionalskus")).getOrElse(Set()),
      Try(row.getMap[String,DateTime]("stateflags")).getOrElse(Map())
    )
    ) match {
      case Success(account) => Some(account)
      case _                => None
    }
  }

  /**
   * reads in from the cassandra row and query the Cassandra table
   *
   * @param row inputs from Cassandra
   * @return
   */
  def createFromCassandraRow(row: CassandraRow, tenant: String): Option[SubscriberAccount] = {
    val defaultDateTime = new DateTime(0)
    val emptyString = "null"
    val tenantId = tenant

    Try(new SubscriberAccount(row.getString("accountid"),
      row.getStringOption("defaultuserid").getOrElse( emptyString ),
      row.getSet[String]("deviceids"),
      row.getBooleanOption("disabled").getOrElse(false),
      row.getBooleanOption("dvrnotificationsenabled").getOrElse(false),
      row.getSet[String]("groups"),
      row.getStringOption("ipaddress").getOrElse(emptyString),
      row.getStringOption("legacyaccountexternalid").getOrElse(emptyString),
      row.getStringOption("legacybranchid").getOrElse(emptyString),
      defaultDateTime.toString(),
      row.getBooleanOption("pbroverride").getOrElse(false),
      row.getSet[String]("userids"),
      row.getSet[String]("profileids"),
      row.getStringOption("accounttype").getOrElse(emptyString),
      Try(row.getString("mediafirstrequiredsku").split(" ").mkString("-")).getOrElse( emptyString),
      Try(row.getSet[String]("mediafirstoptionalskus")).getOrElse(Set()),
      Try(row.getMap[String,DateTime]("stateflags")).getOrElse(Map()),
      tenantId))
    match {
      case Success(account) => Some(account)
      case _                => None
    }
  }

}
