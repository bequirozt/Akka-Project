ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"
lazy val AkkaVersion = "2.6.20"
lazy val cassandraVersion = "1.0.6"
lazy val akkaHttpVersion = "10.2.10"
lazy val root = (project in file("."))
  .settings(
    name := "ecommerce"
  )

libraryDependencies ++= Seq(
  //Serialization
  //  "com.typesafe.akka" %% "akka-util" % AkkaVersion,
  //  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  //HTTP
  "com.typesafe.akka" %% "akka-http"   % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  //Streams
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  //Persistence
  "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % AkkaVersion,


  // Cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,

  //Alpakka
  "com.lightbend.akka" %% "akka-stream-alpakka-file" % "3.0.4",
  "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % "4.0.0",

  "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "4.0.0",

  //Datastax Java Driver for Cassandra && Google Plugin
  "com.datastax.oss"  %  "java-driver-core"  % "4.14.1",   // See https://github.com/akka/alpakka/issues/2556
  "com.google.guava" % "guava" % "31.1-jre",

  // Google Protocol Buffers
  "com.google.protobuf" % "protobuf-java" % "3.21.5",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.14" % "test",
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,

  // Coverage
  //"org.scoverage" % "sbt-scoverage_2.12_1.0" % "2.12"
)