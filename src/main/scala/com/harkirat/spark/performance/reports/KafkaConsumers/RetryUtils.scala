package com.harkirat.spark.performance.reports.KafkaConsumers

/**
 * RetryUtils, because your code deserves another chance.
 * This utility class provides easy to use retry mechanisms for our code
 * Usage:
 * {{{
 *   // Try three times to run somethingThatMightFail() and throw an exception if it keeps failing
 *   val result = RetryUtils.retry(3)(somethingThatMightFail())
 *
 *   // Try three times to run somethingThatMightFail() and returns "defualt value" if it keeps failing
 *   val result = RetryUtils.retryOrElse(3, "default value")(somethingThatMightFail())
 *
 *   // Try three times to run somethingThatMightFail(), waiting for 5000ms between retries and terminates the application if it keeps failing
 *   val result = RetryUtils.retryOrDie(3, 5000)(somethingThatMightFail())
 *
 *   // Try three times to run somethingThatMightFail() and returns "default value" if it keeps failing with functions to be run at each attempt
 *   val result = RetryUtils.retryOrElse("default value", { System.out.println("Failed to get value, retrying ...") },
 *                                                        { System.out.println("Failed to get value, giving-up")}
 *                                      )(somethingThatMightFail())
 * }}}
 * @author eforjul
 */
object RetryUtils {

  /**
   * Error code returned by the application when retryOrDie is used
   */
  private final val DIE_RETURN_CODE = 1

  /**
   * Default time in milliseconds between each retry
   */
  private final val RETRY_DELAY = 1000

  /**
   * Retry running a given statement for a specified amount of time and throw the last exception if the statement keeps failing
   * @param n number of retries
   * @param loopFn a function that is called at each iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty function
   * @param failureFn a function that is called at the final iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty
   *                  function
   * @param fn the statement to be retried
   * @tparam T The parameter to be returned by the statement fn
   * @return The result of the statement fn if it succeeds.
   */
  @annotation.tailrec
  def retry[T](n: Int, retryDelay: Int = RETRY_DELAY, loopFn: => Unit = {}, failureFn: => Unit = {})(fn: => T): T = {
    util.Try { fn } match {
      case util.Success(x) => x
      case _ if n > 1 =>
        loopFn
        Thread.sleep(retryDelay)
        retry(n - 1, retryDelay, loopFn, failureFn)(fn)
      case util.Failure(e) =>
        failureFn
        throw e
    }
  }

  /**
   * Retry running a given statement for a specified amount of time and throw the last exception if the statement keeps failing
   * @param n number of retries
   * @param loopFn a function that is called at each iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty function
   * @param failureFn a function that is called at the final iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty
   *                  function
   * @param fn the statement to be retried
   * @tparam T The parameter to be returned by the statement fn
   * @return The result of the statement fn if it succeeds.
   */
  @annotation.tailrec
  def retryWhen[T](n: Int, retryDelay: Int = RETRY_DELAY, loopFn: => Unit = {}, failureFn: => Unit = {}, throwableParserFn: Throwable => Unit = defaultThrowableParserFn, conditionFn: Throwable => Boolean = (_=>true))(fn: => T): T = {
    util.Try { fn } match {
      case util.Success(x) => x
      case util.Failure(e) if (n > 1 && conditionFn(e)) =>
        throwableParserFn(e)
        loopFn
        Thread.sleep(retryDelay)
        retryWhen(n - 1, retryDelay, loopFn, failureFn, throwableParserFn, conditionFn)(fn)
      case util.Failure(e) =>
        failureFn
        throw e
    }
  }

  /**
   * Retry running a given statement for a specified amount of time and returns a default value if the statement keeps failing
   * @param n number of retries
   * @param default default value to be returned if the statement keeps failing
   * @param loopFn a function that is called at each iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty function
   * @param failureFn a function that is called at the final iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty
   *                  function
   * @param fn the statement to be retried
   * @tparam T The parameter to be returned by the statement fn
   * @return The result of the statement fn if it succeeds, the default value otherwise
   */
  @annotation.tailrec
  def retryOrElse[T](n: Int, default: T,  retryDelay: Int = RETRY_DELAY, loopFn: => Unit = {}, failureFn: => Unit = {})(fn: => T): T = {
    util.Try { fn } match {
      case util.Success(x) => x
      case _ if n > 1 =>
        loopFn
        Thread.sleep(retryDelay)
        retryOrElse(n - 1, default, retryDelay, loopFn, failureFn)(fn)
      case util.Failure(e) =>
        failureFn
        default
    }
  }

  /**
   * Retry running a given statement for a specified amount of time and terminate the application if the statement keeps failing
   * @param n number of retries
   * @param returnCode code returned by the application if the statement keeps failing and the application terminates
   * @param loopFn a function that is called at each iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty function
   * @param failureFn a function that is called at the final iteration (retry) of the process. Can be used to send logs for example. Defaults to an empty
   *                  function
   * @param fn the statement to be retried
   * @tparam T The parameter to be returned by the statement fn
   * @return The result of the statement fn if it succeeds, the default value otherwise
   */
  @annotation.tailrec
  def retryOrDie[T](n: Int, returnCode: Int = DIE_RETURN_CODE,  retryDelay: Int = RETRY_DELAY, loopFn: => Unit = {}, failureFn: => Unit = {},
                    throwableParserFn: Throwable => Unit = defaultThrowableParserFn)(fn: => T): T = {
    util.Try { fn } match {
      case util.Success(x) => x
      case _ if n > 1 =>
        loopFn
        Thread.sleep(retryDelay)
        retryOrDie(n - 1, returnCode, retryDelay, loopFn, failureFn, throwableParserFn)(fn)
      case util.Failure(e) =>
        failureFn
        throwableParserFn(e)
        System.exit(returnCode)
        throw e
    }
  }

  private def defaultThrowableParserFn(e: Throwable) = { }

}

