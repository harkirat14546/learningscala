
name := "PerfomanceMetricsReport"

version := "0.6"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq (
  "com.ericsson.mediafirst"    %% "spark-utils"                  % "1.0.+",
  "com.ericsson.mediafirst"    %% "config"                       % "1.1.+",
  "com.ericsson.mediafirst"    %% "spark-transformation-provider"% "1.0.+",
  "com.ericsson.mediafirst"    %% "transport-provider"           % "1.0.+",
  "com.ericsson.mediafirst"    %% "spark-kafka-provider"         % "2.0.+",
  "com.ericsson.mediafirst"    %% "serialization"                % "1.1.+",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "2.4.0",
 // "org.apache.spark" %% "spark-sql" % "2.3.0",
  "org.apache.logging.log4j"   %"log4j-core"                        %"2.11.0",
  "org.apache.logging.log4j"   %"log4j-api"                         %"2.11.0",
  "org.scalatest"                %% "scalatest"            % "3.0.5"	% "test",
  "org.apache.hadoop" % "hadoop-aws" % "2.7.3",
  "com.microsoft.azure" % "azure-storage-blob" % "10.0.1-Preview",
  "com.microsoft.azure" % "azure-storage" % "1.2.0"
)
assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

// META-INF discarding
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assemblyShadeRules in assembly ++= Seq(
  ShadeRule.rename("io.netty.**" -> "shade.@1").inAll,
  ShadeRule.rename("com.google.**" -> "shadeio.@1").inAll
  //    ShadeRule.rename("javax.inject.**" -> "shade2.@1").inAll
)