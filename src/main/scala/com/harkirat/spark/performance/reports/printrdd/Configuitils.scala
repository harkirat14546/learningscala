package com.harkirat.spark.performance.reports.printrdd

import java.io.File

import com.ericsson.mediafirst.utils.config.ConfigUtils
import com.ericsson.mediafirst.utils.serialization.JsonUtils
import com.ericsson.mediafirst.utils.tools.DataDef._
import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MutableMap}
import scala.io.Source
import scala.util.Try
import scala.util.parsing.json.JSON
import scala.xml.XML

/** An helper class to manage configuration and configuration fallbacks between different sources
 *
 * @author eforjul
 */
object ConfigUtils {

  /**
   * Name of the configuration root for Mediafirst specific configuration
   */
  private final val MF_CONFIG_ROOT =            "mediafirst"
  /**
   * Path to the local file system configuration file that will be parsed and prioritized if found
   */
  private final val LOCAL_FILE_PATH =           "./application.conf"
  /**
   * Default path to the RCS xml file on the target file-system
   */
  private final val RCS_DEFAULT_LOCATION =      "/etc/tv3/rcs/analyticssparkjobs.xml"
  /**
   *Default path to the Bootstrap json file on the target file-system
   */
  private final val BOOTSTRAP_DEFAULT_LOCATION =      "/etc/tv3/bootstrap/bootstrap.json"
  /**
   * A Map that for each configuration key stores the origin of the configuration value. Possible values are taken from FETCH_* constants
   */
  private val keyOrigins: MutableMap[String, String] = MutableMap[String, String]()
  /**
   * Configuration value was fetched from the configuration file supplied on the local file system in the current folder
   */
  private final val FETCHED_LOCAL =             "Fetched from local configuration"
  /**
   * Configuration value was fetched from the configuration file in the jar resources folder
   */
  private final val FETCHED_RESOURCES =         "Fetched from jar resources"
  /**
   * Configuration value was fetched from the analyticssparkjobs.json RCS external file in the global common repository (RCSProd/common/)
   */
  private final val FETCHED_RCS_JSON_COMMON =   "Fetched from RCS common json"
  /**
   * Configuration value was fetched from the analyticssparkjobs.json RCS external file in the global common repository RCSxxx/operator/common)
   */
  private final val FETCHED_RCS_JSON_OPERATOR = "Fetched from RCS operator json"
  /**
   * Configuration value was fetched from the analyticssparkjobs.json RCS external file in the operator environment repository RCSxxx/operator/env)
   */
  private final val FETCHED_RCS_JSON_ENV =      "Fetched from RCS environment Json"
  /**
   * Configuration value was fetched from the analyticssparkjobs.json RCS external file in the operator environment site repository RCSxxx/operator/envSite)
   */
  private final val FETCHED_RCS_JSON_SITE =      "Fetched from RCS site Json"
  /**
   * Configuration value was fetched from the analyticssparkjobs.json RCS XML
   */
  private final val FETCHED_RCS_XML =           "Fetched from RCS xml"

  private var parsedConfig: Config = null

  def getConfig: Config = getConfig()

  def getConfig(pairs: (String, Any)*): Config = getConfig(pairs.toList)


  /** Loads configuration object from 3 sources with the following priorities:
   *  - configuration key values are first read from whatever is provided to the getConfig call
   *  - configuration key values are first read from an "application.conf" file in the current directory of the local file-system
   *  - configuration key values from the application.conf in the jar resources directory are added (if missing) to the configuration object
   *  - in the resulting configuration object, for each key, the value is updated if it starts with "RCS." fetching data from RCS in the following order:
   *    - from the embedded json object in the global common RCS file
   *    - from the embedded json object in the operator common RCS file
   *    - from the embedded json object in the operator environment RCS file
   *    - from the embedded json object in the operator environment site RCS
   *    - from the RCS xml key value files
   *  If the value name is RCS._, then it will be replaced by looking for the same key name in RCS, If not, the looked-up key will be the string following
   *  "RCS.", for example, the key for "RCS.myKey" will be "myKey"
   * @param pairs: a list of key value pair that override the config object
   * @return the resulting typesafe Config object for the mediafirst root (other configurations are not included)
   */
  def getConfig(pairs: List[(String, Any)]): Config = {
    if (parsedConfig != null) return parsedConfig
    // Create a first config object from provided pairs
    val argConfig = createConfigFromArgs(pairs)
    getConfig(argConfig)
  }

  def getConfig(argConfig: Config): Config = {
    if (parsedConfig != null) return parsedConfig
    // Get local file system config and update key origins accordingly
    val providedConfig = argConfig.withFallback(ConfigFactory.load(ConfigFactory.parseFile(new File(LOCAL_FILE_PATH))))
    if (providedConfig.hasPath(MF_CONFIG_ROOT)) for (entry <- providedConfig.getConfig(MF_CONFIG_ROOT).entrySet) keyOrigins += (entry.getKey -> FETCHED_LOCAL)
    // Get resources config and update key origins accordingly
    var config = providedConfig.withFallback(ConfigFactory.load()).getConfig(MF_CONFIG_ROOT)
    for (entry <- config.entrySet) if (!keyOrigins.contains(entry.getKey)) keyOrigins += (entry.getKey -> FETCHED_RESOURCES)
    // Parse all rcs key values from the RCS file on the target machine
    val rcsPath = if (config.hasPath("rcs.filePath")) config.getString("rcs.filePath") else RCS_DEFAULT_LOCATION
    val rcsMap = loadRcsEntries(rcsPath)
    val rcsSiteJsonMap = if (rcsMap.contains("AnalyticsSparkJobsSite")) getRcsJsonMap(rcsMap("AnalyticsSparkJobsSite")) else emptyDataMap
    val rcsEnvJsonMap = if (rcsMap.contains("AnalyticsSparkJobsEnvironment")) getRcsJsonMap(rcsMap("AnalyticsSparkJobsEnvironment")) else emptyDataMap
    val rcsOperatorJsonMap = if (rcsMap.contains("AnalyticsSparkJobsOperator")) getRcsJsonMap(rcsMap("AnalyticsSparkJobsOperator")) else emptyDataMap
    val rcsCommonJsonMap = if (rcsMap.contains("AnalyticsSparkJobsCommon")) getRcsJsonMap(rcsMap("AnalyticsSparkJobsCommon")) else emptyDataMap
    // Replace "RCS.*" values in config with corresponding RCS values
    for (entry <- config.entrySet()) config = updateConfig(config, entry.getKey, entry.getValue, rcsSiteJsonMap, rcsEnvJsonMap, rcsOperatorJsonMap,
      rcsCommonJsonMap, rcsMap)
    parsedConfig = config
    config
  }

  def createConfigFromArgs(pairs: List[(String, Any)]): Config = {
    pairs.foldLeft(ConfigFactory.empty)((config, pair) => config.withValue("mediafirst." + pair._1, ConfigValueFactory.fromAnyRef(pair._2)))
  }

  /** Parses and loads an RCS XML file into a Map[String, String] containing all the key values in the RCS file
   *
   * @param path path to the RCS file
   * @return Map of RCS key values
   */
  def loadRcsEntries(path: String): Map[String, String] = {
    val xml = XML.loadFile(path)
    val entries = xml \\ "value"
    entries.flatMap(entry => if (entry.attribute("key").isDefined) List(entry.attribute("key").get.toString -> entry.text) else List()).toMap
  }

  /** Convert a configuration object into a String with all the configuration key values line by line. In addition, the origin of all the configuration values
   * are specified
   *
   * @param config the config to be turned into a String
   * @return the configuration as a printable String
   */
  def toString(config: Config): String = {
    config.entrySet().toList.sortWith(_.getKey < _.getKey).map(entry => entry.getKey + ": " + entry.getValue.render + " "
      + keyOrigins.getOrElse(entry.getKey, "appended"))
      .foldLeft("")((x,y) => x + "\n" + y)
  }

  /** Parses an RCS embedded json entry into a Map[String, Any] if the json is valid or an empty map if it is not
   *
   * @param json the json string to be parsed
   * @return the resulting Map of key values if the json is valid, an empty map otherwise
   */
  def getRcsJsonMap(json: String): DataMap = {
    JsonUtils.parseJson[DataMap](json).fold(Map(), identity)
  }

  /** Create a new configuration object in which the specified "RCS.*" value is replaced by the corresponding value in RCS.
   * @see [[ConfigUtils.getConfig]]
   *
   * @param config The original config object
   * @param key the key of the value to be updated
   * @param configValue the initial ConfigValue of the value to be updated
   * @param rcsSiteJsonMap the key value map of the RCS Json data read from the operator environment's site RCS
   * @param rcsEnvJsonMap the key value map of the RCS Json data read from the operator environment RCS
   * @param rcsOperatorJsonMap the key value map of the RCS Json data read from the operator common RCS
   * @param rcsCommonJsonMap the key value map of the RCS Json data read from the global common RCS
   * @param rcsMap the key value map of the RCS XML data
   * @return a new configuration object in which the provided key's value has been updated according to the RCS replacement rules
   */

  def updateConfig(config: Config, key: String, configValue: ConfigValue, rcsSiteJsonMap: DataMap, rcsEnvJsonMap: DataMap,
                   rcsOperatorJsonMap: DataMap, rcsCommonJsonMap: DataMap, rcsMap: Map[String, String]): Config = {

    if (configValue.valueType() != com.typesafe.config.ConfigValueType.STRING) return config
    val value = config.getString(key)
    if (!value.startsWith("RCS.")) return config

    // RCS key end with ._csv      e.g. RCS.HmrKeys._csv
    // the value is a comma seperated list
    // for each element, use it as a key to pull value from RCS and populate the config to spark config
    if (value.endsWith("._csv")) {
      return enrichConfigFromCsv(value.drop(4).dropRight(5), key, config, rcsMap)
    }

    // RCS key end with ._json      e.g. RCS.analyticsDataConfig._json
    // this json is composed of key-datamap pairs. each pair is populated to spark config
    if (value.endsWith("._json")) {
      return enrichConfigFromJson(value.drop(4).dropRight(6), key, config, rcsMap)
    }

    val rcsKey = if (value.equals("RCS._")) key else parseValue(value.drop(4), config)

    // First try RCS XML key-values
    if (rcsMap.contains(rcsKey)) {
      keyOrigins += (key -> FETCHED_RCS_XML)
      return config.withValue(key, ConfigValueFactory.fromAnyRef(rcsMap(rcsKey)))
    }
    // Then try the site json
    if (rcsSiteJsonMap.contains(rcsKey)) {
      keyOrigins += (key -> FETCHED_RCS_JSON_SITE)
      return config.withValue(key, ConfigValueFactory.fromAnyRef(rcsSiteJsonMap(rcsKey)))
    }
    // Then try environment json
    if (rcsEnvJsonMap.contains(rcsKey)) {
      keyOrigins += (key -> FETCHED_RCS_JSON_ENV)
      return config.withValue(key, ConfigValueFactory.fromAnyRef(rcsEnvJsonMap(rcsKey)))
    }
    // Then try operator common json
    if (rcsOperatorJsonMap.contains(rcsKey)) {
      keyOrigins += (key -> FETCHED_RCS_JSON_OPERATOR)
      return config.withValue(key, ConfigValueFactory.fromAnyRef(rcsOperatorJsonMap(rcsKey)))
    }
    // Finally try global common json
    if (rcsCommonJsonMap.contains(rcsKey)) {
      keyOrigins += (key -> FETCHED_RCS_JSON_COMMON)
      return config.withValue(key, ConfigValueFactory.fromAnyRef(rcsCommonJsonMap(rcsKey)))
    }
    // If all fail, return original configuration object
    config
  }

  /**
   * enrich the config by picking up the config in RCS referenced in the comma seperated list
   * @param rcsKey RCS key
   * @param configKey  config key pointing to a comma seperated list of keys. each key point to a seperate RCS config which will be populated to spark config
   * @param config enrich to this config
   * @param rcsMap RCS data map
   * @return a new config with configIn + populated configs
   */
  private def enrichConfigFromCsv(rcsKey: String, configKey: String, config: Config, rcsMap: Map[String, String]): Config = {
    var configEnriched = config
    if (rcsMap.contains(rcsKey)) {
      keyOrigins += (configKey -> FETCHED_RCS_XML)
      val rcsKeysCsv = rcsMap(rcsKey)
      configEnriched = configEnriched.withValue(configKey, ConfigValueFactory.fromAnyRef(rcsKeysCsv))
      // get config key prefix
      val endIdx = configKey.lastIndexOf('.')
      val keyPrefix = if (endIdx >= 0) configKey.substring(0, endIdx) else ""
      // use each comma seperated item as key to get value from RCS, then add the kvp to config
      val rcsSubKeys = rcsKeysCsv.split(",").map(_.trim)
      for (rcsSubKey <- rcsSubKeys) {
        val newConfigKey  = keyPrefix + "." + rcsSubKey
        val configValue = Try(rcsMap(rcsSubKey)).getOrElse("")
        configEnriched = configEnriched.withValue(newConfigKey, ConfigValueFactory.fromAnyRef(configValue))
      }
    }
    configEnriched
  }

  /**
   * enrich the config by picking
   * @param rcsKey RCS key
   * @param configKey  config key pointing to a json resource in RCS. the json represent a list of RCS resources to be populated to seperated spark config
   * @param config enrich to this config
   * @param rcsMap RCS data map
   * @return a new config with configIn + populated configs
   */
  private def enrichConfigFromJson(rcsKey: String, configKey: String, config: Config, rcsMap: Map[String, String]): Config = {
    var configEnriched = config
    if (rcsMap.contains(rcsKey)) {
      val configJson = rcsMap(rcsKey)
      val configMap = getRcsJsonMap(configJson)
      // get config key prefix by dropping the last '.' and on. e.g. healthModelRule.HmrKeys yield this prefix healthModelRule
      val endIdx = configKey.lastIndexOf('.')
      val keyPrefix = if (endIdx >= 0) configKey.substring(0, endIdx) else ""
      // populate the key-values from json
      val configKeys = configMap.keys
      for (key <- configKeys) {
        val newConfigKey  = keyPrefix + "." + key
        val configValue = configMap(key).asInstanceOf[DataMap]
        configEnriched = configEnriched.withValue(newConfigKey, ConfigValueFactory.fromMap(configValue))
      }
    }
    configEnriched
  }

  def parseValue(value: String, config: Config): String = {
    val matches = """\%\((.*)\)""".r.findAllMatchIn(value).toList
    if (matches.nonEmpty) {
      val keyPath = matches.head.group(1)
      if (config.hasPath(keyPath)) {
        matches.foldLeft(value)((result, matched) => result.replace(s"${matched.matched}", config.getString(keyPath)))
      } else value
    } else value
  }

  /**
   * get the slot
   * @return slot
   */
  //TODO get the slot from bootstap file after salt deployment is ready
  def getSlot: String = {
    //val config = this.getConfig
    val bootstrapJson = Source.fromFile(BOOTSTRAP_DEFAULT_LOCATION).getLines().mkString
    JSON.parseFull(bootstrapJson) match {
      case Some(x) => {
        val obj = x.asInstanceOf[Map[String,Object]].getOrElse("app_config_key_value_pairs",emptyDataMap).asInstanceOf[Map[String,String]]
        obj.getOrElse("slot","S1")
      }
      case None => "S1"
    }
    //if (config.hasPath("slot")) config.getString("slot") else "s1"
  }

  /** returns a configuration value from a typesafe.config object by looking for deepest matching key in the tree. See unit tests for more details
   *
   * @param config the typesafe.Config object where the value is looked for
   * @param prefix the deepest leaf of the tree where to look for the key
   * @param key the key for the searched value
   * @return the key value if the key is found, "" otherwise
   */
  def getDeepestKeyName(config: Config, prefix: String, key: String): String = {
    if (config.hasPath(prefix + "." + key)) return prefix + "." + key
    val parts = prefix.split("\\.")
    for (i <- parts.length - 1 to 1 by -1) {
      var subPrefix = ""
      for (j <- 0 to i - 1) subPrefix = subPrefix + parts(j) + "."
      if (config.hasPath(subPrefix + key)) return subPrefix + key
    }
    if (config.hasPath(key)) return key
    ""
  }

}

