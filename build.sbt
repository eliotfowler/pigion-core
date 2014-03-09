name := "pigion"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "org.postgresql" % "postgresql" % "9.2-1002-jdbc4",
  "nl.rhinofly" %% "play-s3" % "3.3.4"
)

resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"

play.Project.playScalaSettings
