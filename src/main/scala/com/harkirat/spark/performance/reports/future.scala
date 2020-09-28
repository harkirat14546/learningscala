package com.harkirat.spark.performance.reports

import concurrent.ExecutionContext.Implicits.global
import concurrent.Future
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.joda.time.DateTime

object future extends  App{

  def getConfigWithTime(start: DateTime, stop: DateTime, kafkaConfigKey: String)(implicit config: Config): Config = {
    config.getConfig(kafkaConfigKey)
      .withValue("period.start", ConfigValueFactory.fromAnyRef(start.toString))
      .withValue("period.stop", ConfigValueFactory.fromAnyRef(stop.toString))
  }
}
