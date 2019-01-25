organization in Global := "com.gluegadget.cr4s"

scalaVersion in Global := "2.12.8"

lazy val cr4s = project.in(file(".")).aggregate(core, example)

lazy val core = project

lazy val example = (project dependsOn core)
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings ++ inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "it,test"
  )
