package com.harkirat.spark.performance.reports.scalalearning

object searching {

  def binarysearch(arr:List[Int],element:Int,left:Int,right:Int):AnyVal={
    val mid:Int=(left+right)/2
    if (arr(mid)==element){
      mid
    }
    else if( element < arr(mid)) {
      binarysearch(arr,element,0,mid-1)
    }
    else if ( element> arr(mid)) {
      binarysearch(arr,element,mid+1,arr.length-1)
    }
    else
      -1
  }
}
