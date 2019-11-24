name := "dash"
version := "1.0"

scalaVersion := "2.13.1"

lazy val `dash` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"

libraryDependencies ++= Seq(
  ehcache,
  ws,
  guice,
  "org.scala-lang.modules"   %% "scala-xml"                % "1.2.0",
  "com.typesafe.play"        %% "play-slick"               % "4.0.2",
  "com.typesafe.play"        %% "play-slick-evolutions"    % "4.0.2",
  "com.github.tminglei"      %% "slick-pg"                 % "0.18.0",
  "com.github.tminglei"      %% "slick-pg_play-json"       % "0.18.0",
  "com.atlassian.commonmark" % "commonmark"                % "0.13.0",
  "com.atlassian.commonmark" % "commonmark-ext-gfm-tables" % "0.13.0",
  "com.google.cloud"         % "google-cloud-vision"       % "1.97.0"
)

pipelineStages := Seq(digest, gzip)

TwirlKeys.templateImports ++= Seq(
  "controllers._",
  "model._",
  "model.charter._"
)

enablePlugins(DockerPlugin)

packageName := "fs-dash"

dockerBaseImage := "openjdk:11-slim"
dockerUsername := Some("galedric")
dockerUpdateLatest := true
