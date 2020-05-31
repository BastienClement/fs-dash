logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin"          % "2.8.2")
addSbtPlugin("org.irundaia.sbt"  % "sbt-sassify"         % "1.4.13")
addSbtPlugin("com.typesafe.sbt"  % "sbt-digest"          % "1.1.4")
addSbtPlugin("com.typesafe.sbt"  % "sbt-gzip"            % "1.0.2")
addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager" % "1.3.25")
