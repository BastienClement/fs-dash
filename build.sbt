name := "dash"
version := "1.0"

scalaVersion := "2.13.2"

lazy val `dash` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"

libraryDependencies ++= Seq(
  ehcache,
  ws,
  guice,
  "org.scala-lang.modules"   %% "scala-xml"                % "1.3.0",
  "com.typesafe.play"        %% "play-slick"               % "5.0.0",
  "com.typesafe.play"        %% "play-slick-evolutions"    % "5.0.0",
  "com.github.tminglei"      %% "slick-pg"                 % "0.19.0",
  "com.github.tminglei"      %% "slick-pg_play-json"       % "0.19.0",
  "com.atlassian.commonmark" % "commonmark"                % "0.15.1",
  "com.atlassian.commonmark" % "commonmark-ext-gfm-tables" % "0.15.1"
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

javaOptions in Runtime ++= Seq(
  "DISCORD_OAUTH_SECRET",
  "DISCORD_BOT_TOKEN",
  "BNET_OAUTH_SECRET"
).map(key => key -> sys.env.get(key)).collect {
  case (key, Some(value)) => s"-D$key=$value"
}
