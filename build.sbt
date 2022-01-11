ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

Global / onChangedBuildSource := IgnoreSourceChanges

lazy val root = (project in file(".")).settings(
  name := "kafka-streaming-rockjvm"
)

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-clients" % V.kafka,
  "org.apache.kafka" % "kafka-streams" % V.kafka,
  "org.apache.kafka" %% "kafka-streams-scala" % V.kafka,
  "io.circe" %% "circe-core" % V.circe,
  "io.circe" %% "circe-generic" % V.circe,
  "io.circe" %% "circe-parser" % V.circe
)
