package com.harkirat.spark.performance.reports

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{col, get_json_object, split, when}
import org.apache.spark.sql.UDFRegistration
import org.apache.spark.sql.functions.{col,udf}

object makeReportStructure {

  def makeReportStructure(failureDf: DataFrame, ss:SparkSession) = {
    import ss.implicits._
    val failureDataframe = failureDf.select(
      get_json_object($"Payload","$.StartTimeUtc").alias("StartTimeUtc"),
      get_json_object($"Payload","$.Total").alias("Total"),
      get_json_object($"Payload","$.Name").alias("Name"),
      get_json_object($"Payload","$.EndTimeUtc").alias("EndTimeUtc"),
      get_json_object($"Payload","$.Count").alias("Count"),
      get_json_object($"Payload","$.stsTenantId").alias("TenantId")

    ).toDF()
    
    
    val getApiStatus= ss.udf.register("getApiStatus",( x:String)=> { val y=x.split("\\.");
      y(y.length-2)
    })

    val getApiName= ss.udf.register("getApiName",( x:String)=> { val y=x.split("\\.");
      y.slice(0,y.length-2).mkString(".")
    })

    val reportStruct = failureDataframe.select(
      $"StartTimeUtc".as("Start Time"),
      $"Count",
      when(col("Name").isNull
        ,"Null").otherwise(getApiStatus(col("Name"))).
        as("Api Status"),
      when( col("Name").contains("Get"),"Get").
        when(col("Name").contains("Post"),"Post").otherwise("Null")
      .as("Request Method"),
      when(col("Name").isNull,"Null").otherwise(getApiName(col("Name"))).
        as("API Name"),
      when($"Total".isNotNull and $"Count".isNotNull,($"Total"/$"Count")/10000).
        otherwise("0").as("Average Response Time"),
      $"EndTimeUtc".as("End Time"),
      $"TenantId"
    ).toJSON
    reportStruct
  }
}
