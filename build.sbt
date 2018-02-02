name := "akka-quickstart-scala"
version := "1.0"
scalaVersion := "2.11.8"

lazy val akkaVersion = "2.5.1"
lazy val akkaHTTPVersion = "10.0.6"

enablePlugins(JavaAppPackaging)


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.0.6",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.4",
  "ml.combust.mleap" %% "mleap-runtime" % "0.9.0",
  //"com.typesafe.akka" %% "akka-stream-experimental" % "1.0-M3",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1",
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.0",
  "com.swiggy.feature_extractor" % "feature_extractor_2.11" % "0.0.1-SNAPSHOT",
  "com.swiggy.store" % "feature_store_2.11" % "0.0.1-SNAPSHOT",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "org.json4s" %% "json4s-jackson" % "3.6.0-M2",
  "org.json4s" %% "json4s-core" % "3.6.0-M2",
  "org.apache.avro" % "avro" % "1.8.2",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.0",
  "com.sclasen" %% "akka-zk-cluster-seed" % "0.1.8",
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.1.0",
  "mysql" % "mysql-connector-java" % "5.1.21",
  "org.apache.curator" % "curator-framework" % "2.12.0",
  "org.apache.curator" % "curator-recipes" % "2.12.0",
  "org.apache.curator" % "curator-client" % "2.12.0",
  "com.swiggy.model-api" %% "model_api" % "0.0.1-SNAPSHOT",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.9",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "com.getsentry.raven" % "raven-logback" % "7.6.0",
  "net.cakesolutions" %% "scala-kafka-client" % "0.10.1.2",
  "net.cakesolutions" %% "scala-kafka-client-akka" % "0.10.1.2",
  "net.cakesolutions" %% "scala-kafka-client-testkit" % "0.10.1.2" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

libraryDependencies ++= Seq(
  "org.aspectj" % "aspectjweaver" % "1.8.13",
"com.workday" %% "prometheus-akka" % "0.8.4",
  "io.kamon" %% "kamon-core" % "1.0.0",
  "io.kamon" %% "kamon-akka-2.5" % "1.0.0",
  "io.kamon" %% "kamon-prometheus" % "1.0.0",
  "io.kamon" %% "kamon-zipkin" % "1.0.0",
  "io.kamon" %% "kamon-jaeger" % "1.0.0"
)

resolvers += "Local Maven Repository" at "file:///"+ Path.userHome + "/.m2/repository"

resolvers += Resolver.jcenterRepo

resolvers ++= Seq(
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "clojars" at "https://clojars.org/repo",
  "conjars" at "http://conjars.org/repo"
)

resolvers += Resolver.bintrayRepo("cakesolutions", "maven")
resolvers += Resolver.jcenterRepo

assemblyMergeStrategy in assembly := {
  case PathList("reference.conf") => MergeStrategy.concat
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("org.apache.commons.io.**" -> "shadeio.@1").inAll
)
