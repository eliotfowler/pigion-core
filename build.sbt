name := "pigion"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "nl.rhinofly" %% "play-s3" % "3.3.4",
  "com.sksamuel.scrimage" % "scrimage-core_2.10" % "1.3.15",
  "ws.securesocial" %% "securesocial" % "3.0-M1-play-2.2.x",
  "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.typesafe" %% "play-plugins-redis" % "2.2.0"
)


resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"

resolvers += "Sedis Repo" at "http://pk11-scratch.googlecode.com/svn/trunk/"

resolvers += Resolver.sonatypeRepo("releases")

play.Project.playScalaSettings
