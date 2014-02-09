name := "pigion"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "org.postgresql" % "postgresql" % "9.2-1002-jdbc4"
)     

play.Project.playScalaSettings
