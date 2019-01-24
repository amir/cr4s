libraryDependencies ++= Seq(
  "com.softwaremill.quicklens" %% "quicklens" % "1.4.11"
)

scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-language:higherKinds"
)
