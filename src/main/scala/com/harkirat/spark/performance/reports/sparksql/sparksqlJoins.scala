package com.harkirat.spark.performance.reports.sparksql

import org.apache.spark.sql.SparkSession


object sparksqlJoins extends  App {
 case class Person(name:String,age:String)
  val ss=SparkSession.builder().appName("joins").master("local[*]").getOrCreate()
  import ss.implicits._
  val dfjs=ss.read.json("/Users/mediakind/studymaterial/study_material/Spark/dataset.json")
   .as[Person]
case class depart(dept_name:String,dept_id:Int)
  val emp=Seq((1,"Smith",-1,"2018","10","M",3000),
    (2,"Rose",1,"2010","20","M",4000),
    (3,"Williams",1,"2010","10","M",1000),
    (4,"Jones",2,"2005","10","F",2000),
    (5,"Brown",2,"2010","40","",-1),
    (6,"Brown",2,"2010","50","",-1))

  val empcolumes=Seq("emp_id","name","superior_emp_id","year_joined",
  "emp_dept_id","gender","salary")
  import ss.implicits._
  val empdf=emp.toDF(empcolumes:_*)

  empdf.show(false)

  val dept = Seq(("Finance",10),
    ("Marketing",20),
    ("Sales",30),
    ("IT",40)
  )

  val deptColumns = Seq("dept_name","dept_id")
  val deptDF = dept.toDF(deptColumns:_*)
  deptDF.show(false)
  val df=empdf.join(deptDF,empdf("emp_dept_id")===deptDF("dept_id"),"leftanti")
  df.show()
}

