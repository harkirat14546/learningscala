package com.harkirat.spark.performance.reports.Cassandrautils


import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Days}

import scala.util.Try

//TODO scaladoc
object TimeUtils {
  def getSampleTime(tt:DateTime): DateTime = {
    new DateTime( tt.year().get(), tt.monthOfYear().get(), tt.dayOfMonth().get(),tt.hourOfDay().get(), tt.minuteOfHour().get(), 0)
  }

  def stringToDate(dateString: String, patternString: String = "yyyy-MM-dd'T'HH:mm:ss"): DateTime = {
    val datetimes = patternString.split("\\|").flatMap(strPatten => {
      val fmt = DateTimeFormat.forPattern(strPatten)
      try {
        Some(fmt.parseDateTime(dateString))
      }
      catch {
        case ex: IllegalArgumentException => Try(new DateTime(dateString.toLong)).toOption
      }
    })

    if(datetimes.length == 0) throw new IllegalArgumentException(s"$dateString is malformed")
    datetimes(0)
  }

  def getTableSuffix(date: DateTime): String = {
    val timeFormat = DateTimeFormat.forPattern("yyyyMMdd")
    timeFormat.print(date)
  }

  def convertDateTimeToString(tt: DateTime, pattern: String): String = {
    val fmt = DateTimeFormat.forPattern(pattern).withZoneUTC
    fmt.print(tt)
  }

  /**
   * round to the minute
   *
   * @param time input time
   * @return round down to minute
   */
  def getMinute(time: DateTime): DateTime = {
    new DateTime(time.getYear, time.getMonthOfYear, time.getDayOfMonth, time.getHourOfDay, time.getMinuteOfHour, 0, 0)
  }

  /**
   * round to the minute
   *
   * @param time input time
   * @return round down to minute
   */
  def getDay(time: DateTime): DateTime = {
    new DateTime(time.getYear, time.getMonthOfYear, time.getDayOfMonth, 0, 0, 0, 0)
  }

  /** Returns the earliest of the provided DateTimes
   *
   * @param values a list of DateTimes
   * @return the earliest of the provided DateTimes
   */
  def min(values: DateTime*): DateTime = {
    if (values.isEmpty) throw new IllegalArgumentException("TimeUtils.min cannot be called with an empty list of DateTimes")
    values.fold(values(0))((x, y) => if (x.isBefore(y)) x else y)
  }

  /** Returns the latest of the provided DateTimes
   *
   * @param values a list of DateTimes
   * @return the latest of the provided DateTimes
   */
  //TODO unittest
  def max(values: DateTime*): DateTime = {
    if (values.isEmpty) throw new IllegalArgumentException("TimeUtils.max cannot be called with an empty list of DateTimes")
    values.fold(values(0))((x, y) => if (x.isAfter(y)) x else y)
  }

  /** Generates a list of tuples of DateTimes with each tuple addressing a single day at a time from an execution period
   *
   * @param startUtc the begininning of the execution period
   * @param stopUtc the end of the execution period
   * @return The list of day by day periods
   */
  def generateDayByDayPeriods(startUtc: DateTime, stopUtc: DateTime): List[(DateTime, DateTime)] = {
    if (stopUtc.isBefore(startUtc)) {
      throw new IllegalArgumentException("TimeUtils.generateDayByDayPeriods execution period must have first date earlier than the second")
    }
    if (startUtc.dayOfYear == stopUtc.dayOfYear && startUtc.year == stopUtc.year) {
      if (startUtc.equals(stopUtc)) List() else List[(DateTime, DateTime)]((startUtc, stopUtc))
    } else {
      val endTimeOnSameDay = startUtc.plusDays(1).withMillisOfDay(0)
      List[(DateTime, DateTime)]((startUtc, endTimeOnSameDay)) ++ generateDayByDayPeriods(endTimeOnSameDay, stopUtc)
    }
  }

  def generateDayList(from: DateTime, toDate: DateTime): List[DateTime] = {
    val to = if (toDate.isAfter(toDate.withMillisOfDay(0))) toDate else toDate.minusDays(1)
    if (from.isAfter(to)) List[DateTime]() else {
      val length = Days.daysBetween(from.withMillisOfDay(0), to.withMillisOfDay(0)).getDays
      List.tabulate(length + 1)(id => from.plusDays(id).withMillisOfDay(0))
    }
  }


  /**
   * obtain datetime from string. Hupported froms are:
   * input time format could be dd/MM/yyyy HH:mm:ss, 15 days ago, 22 hours ago, 10 minuts ago etc
   * @param timeString input time string
   * @return datetime
   */
  def getTimeFromString(timeString: String): DateTime =
  {
    // input time format could be dd/MM/yyyy HH:mm:ss, 15 days ago, 22 hours ago, 10 minuts ago etc
    val inputTimeFormat = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss").withZone(DateTimeZone.UTC)
    val regDate = """^([0-9]{2}/[0-9]{2}/20[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2})$""".r
    val regDaysAgo = """^([0-9]+) days ago""".r
    val regHoursAgo = """^([0-9]+) hours ago""".r
    val regMinutesAgo = """^([0-9]+) minutes ago""".r
    val regStartDaysAgo = """^start of ([0-9]+) days ago""".r

    timeString match
    {
      case regDate(date) => inputTimeFormat.parseDateTime(date)
      case regDaysAgo(number) => DateTime.now.minusDays(number.toInt)
      case regHoursAgo(number) => DateTime.now.minusHours(number.toInt)
      case regMinutesAgo(number) => DateTime.now.minusMinutes(number.toInt)
      case regStartDaysAgo(number) => getDay(DateTime.now.minusDays(number.toInt))
      case _ => throw new Exception("unexpected date")
    }
  }

  /**
   *
   * get startTime of daysbefore. if the days == 0, the return value will be start time of today
   * @param days
   * @return
   */
  def startTimeDaysBefore(days : Int, dateTime :DateTime = new DateTime(DateTimeZone.UTC)): DateTime = {
    dateTime.minusDays(days).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
  }

}

