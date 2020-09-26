package com.mediakind.mediafirst.spark.performance.reports.sparklearn

import org.apache.spark.sql.{Dataset, SparkSession}

object Broadcast extends  App {
  case class schema(first:String,last:String,statecode:String,State:String)
  val spark=SparkSession.builder().appName("broadcast").master("local[*]").getOrCreate()
  val order=spark.sparkContext.textFile("/Users/mediakind/studymaterial/study_material/CCA-175Spark/data-master/retail_db/orders/part-00000")
  val ordercompleted=spark.sparkContext.accumulator(0,"order completed")
  val orderfilterout=order.filter( order => {
    ordercompleted +=1
    order.split(",")(3)=="COMPLETE"
  })


  val states=Map(("NY","NewYork"),("CA","Delhi"),("FL","Florida"))
  val statesbroadcast=spark.sparkContext.broadcast(states)
  val data = Seq(("James","Smith","USA","CA"),
    ("Michael","Rose","USA","NE"),
    ("Robert","Williams","USA","CA"),
    ("Maria","Jones","USA","FL")
  )
  val rdd=spark.sparkContext.parallelize(data)
import spark.implicits._
 val rdd2= rdd.map{ x=>
    val state=x._4
    val statename=statesbroadcast.value.get(state).getOrElse("Not Available")
    (x._1,x._2,x._4,statename)
  }.toDF()
  rdd2.show()
}


