package com.mediakind.mediafirst.spark.performance.reports.scalalearning

import scala.collection.mutable.ListBuffer

object ProgramsOnArray {


 // def sumoftwoarray()
  def sumoftwoarray(arr:List[Int],target:Int,flag:String=""): ListBuffer[(Int,Int)] = {
    val newarr=new ListBuffer[Int]()
    val result=new ListBuffer[(Int,Int)]()
    for ( z <- 0 to arr.length-1) {
      var temp=target-arr(z)
      if ( newarr.contains(temp)) {
       // println(temp.toString+","+arr(z))
        var i =(temp,arr(z))
        result += i
      } else { newarr += arr(z)}
    }
    result
  }
}
