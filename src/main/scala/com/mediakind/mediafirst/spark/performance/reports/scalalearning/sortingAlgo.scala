package com.mediakind.mediafirst.spark.performance.reports.scalalearning
import com.mediakind.mediafirst.spark.performance.reports.scalalearning.sorting.insertionsort
import com.mediakind.mediafirst.spark.performance.reports.scalalearning.searching.binarysearch

import scala.collection.mutable.ListBuffer
object sortingAlgo extends  App {

  //println(insertionsort(ListBuffer(3,2,0,1,-1,-2)))
  val arr=List(10,20,30,40,50,60,70,80,90,100)
  println(binarysearch(arr,70,0,arr.length-1))
}
