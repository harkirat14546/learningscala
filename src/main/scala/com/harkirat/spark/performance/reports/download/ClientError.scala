package com.harkirat.spark.performance.reports.download

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.get_json_object

object ClientError {
  def makeReportStructure(clientErrorDF: DataFrame, ss: SparkSession) = {
    import ss.implicits._
    val clientErrorParseDf = clientErrorDF.select(
      get_json_object($"value", "$.startTimeUtc").alias("Time"),
      get_json_object($"value", "$.userId").alias("UserId"),
      get_json_object($"value", "$.accountId").alias("AccountId"),
      get_json_object($"value", "$.ipAddress").alias("IPAddress"),
      get_json_object($"value", "$.accountType").alias("AccountType"),
      get_json_object($"value", "$.deviceType").alias("DeviceType"),
      get_json_object($"value", "$.os").alias("DeviceOS"),
      get_json_object($"value", "$.userAgent").alias("UserAgent"),
      get_json_object($"value", "$.errorCode").alias("ErrorCode"),
      get_json_object($"value", "$.errorType").alias("ErrorType"),
      get_json_object($"value", "$.errorMessage").alias("ErrorMessage"),
      get_json_object($"value", "$.pageUrl").alias("pageurl"),
      get_json_object($"value", "$.errorUrl").alias("errorurl"),
      get_json_object($"value", "$.errorVerb").alias("errorVerb"),
      get_json_object($"value", "$.IsRecoverable").alias("IsRecoverable"),
      get_json_object($"value", "$.transactionid").alias("transactionid"),
      get_json_object($"value", "$.stsTenantId").alias("tenantId")
    ).toDF()
    val getErrorDetails = ss.udf.register("getErrorDetails", (pageUrl: String,errorUrl: String,errorVerb:String,
                                                              IsRecoverable:String,transactionid:String) => {
      "pageUrl->"+pageUrl+"errorUrl->"+errorUrl+"errorVerb->"+errorVerb+"IsRecoverable->"+IsRecoverable+"transactionid->"+transactionid
    })

    val clientErrorParseDfFinal=clientErrorParseDf.select($"Time",$"UserId",$"AccountId",$"IPAddress",$"AccountType",$"DeviceType",$"DeviceOS",
      $"UserAgent",$"ErrorCode",$"ErrorType",$"ErrorMessage",
      getErrorDetails($"pageUrl",$"errorUrl",$"errorVerb",$"IsRecoverable",$"transactionid").alias("ErrorDetails"),$"tenantId")
    clientErrorParseDfFinal

  }

}
