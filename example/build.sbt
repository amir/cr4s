libraryDependencies ++= Seq(
  "com.softwaremill.quicklens" %% "quicklens" % "1.4.11",

  "org.scalatest"     %% "scalatest"           % "3.0.5"  % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.14" % Test
)

scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-language:higherKinds"
)
