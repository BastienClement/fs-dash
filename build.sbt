name := "dash"
version := "1.0"

scalaVersion := "2.13.0"

lazy val `dash` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"

libraryDependencies ++= Seq(
  ehcache,
  ws,
  guice,
  "com.typesafe.play"   %% "play-slick"            % "4.0.2",
  "com.typesafe.play"   %% "play-slick-evolutions" % "4.0.2",
  "com.github.tminglei" %% "slick-pg"              % "0.18.0",
  "com.github.tminglei" %% "slick-pg_play-json"    % "0.18.0"
)

pipelineStages := Seq(digest, gzip)

TwirlKeys.templateImports += "model._"

enablePlugins(DockerPlugin)

packageName := "fs-dash"

dockerBaseImage := "openjdk:11-slim"
dockerUsername := Some("galedric")
dockerUpdateLatest := true
