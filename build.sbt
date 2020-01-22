organization  := "com.ibogomolov"

name          := "ibogomolov-scala-exercise"

scalaVersion  := "2.13.1"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"            % "10.1.10",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.10",
  "com.typesafe.akka" %% "akka-stream" % "2.5.25",
  "com.typesafe.akka" %% "akka-actor" % "2.5.25",
  "com.vividsolutions" % "jts" % "1.13",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.wololo" % "jts2geojson" % "0.14.3"
)
