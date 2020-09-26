package com.mediakind.mediafirst.spark.performance.reports.scalalearning
import com.mediakind.mediafirst.spark.performance.reports.scalalearning.sortingAlgo.arr

import scala.collection.mutable.ListBuffer
object sorting {

  def bubblesort(arr:ListBuffer[Int])= {
    for (i <- 0 to arr.length - 1) {
      for (j <- 0 to arr.length - 2) {
        if (arr(j) > arr(j + 1)) {
          val temp = arr(j + 1)
          arr(j + 1) = arr(j)
          arr(j) = temp
        }}
    }
    arr
  }

  def selectionsort(arr:ListBuffer[Int]) ={
    for ( i <- 0 to arr.length-1) {
      var max=i
      for ( j <- i+1 to arr.length-1) {
        if (arr(max) > arr(j)) {
          max =j
        }
      }
      val temp=arr(i)
      arr(i)=arr(max)
      arr(max)=temp
    }
    arr
  }

  def insertionsort(arr:ListBuffer[Int]):ListBuffer[Int]={
    for ( i <- 1 to arr.length-1) {
      //val value=arr(i)
      var hole=i
      while ( hole > 0 && arr(hole-1)> arr(hole)) {
        val temp=arr(hole)// temp=3
        arr(hole)=arr(hole-1) // arr(1)=3
        arr(hole-1)=temp
        hole = hole-1
      }
    }
    arr
  }
}





