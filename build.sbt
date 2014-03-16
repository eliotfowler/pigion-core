name := "pigion"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "nl.rhinofly" %% "play-s3" % "3.3.4",
  "com.sksamuel.scrimage" % "scrimage-core_2.10" % "1.3.15",
  "ws.securesocial" %% "securesocial" % "2.1.3",
  "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4"
)

resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"

resolvers += Resolver.sonatypeRepo("releases")

play.Project.playScalaSettings
