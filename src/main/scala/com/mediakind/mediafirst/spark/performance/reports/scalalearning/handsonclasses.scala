package com.mediakind.mediafirst.spark.performance.reports.scalalearning

import scala.util.Random


class donut2[T](s:String) {
 def isfavoutrite:Boolean = s=="Cake"
 def applydis[T](dis :T)={
  dis match {
   case x:String => println(s"I am String")
   case x:Double => println(s"I am Double")
  }
 }
}





