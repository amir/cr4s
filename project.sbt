organization in Global := "com.gluegadget.cr4s"

scalaVersion in Global := "2.12.8"

lazy val cr4s = project.in(file(".")).aggregate(core, app)

lazy val core = project

lazy val app = project dependsOn core
