organization in Global := "com.gluegadget.cr4s"

scalaVersion in Global := "2.12.8"

lazy val cr4s = project.in(file(".")).aggregate(core, example)

lazy val core = project

lazy val example = project dependsOn core
