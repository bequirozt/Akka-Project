ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"
lazy val AkkaVersion = "2.6.19" // must be 2.5.13 so that it's compatible with the stores plugins (JDBC and Cassandra)
lazy val cassandraVersion = "1.0.6"
lazy val akkaHttpVersion = "10.2.9"
lazy val root = (project in file("."))
  .settings(
    name := "ecommerce"
  )

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
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
  "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % "3.0.4",

  "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "3.0.4",

  //Datastax Java Driver for Cassandra && Google Plugin
  "com.datastax.oss"  %  "java-driver-core"  % "4.14.1",   // See https://github.com/akka/alpakka/issues/2556
  "com.google.guava" % "guava" % "31.1-jre"


)