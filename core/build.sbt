libraryDependencies ++= Seq(
  "io.skuber"         %% "skuber"      % "2.0.12",
  "com.typesafe.akka" %% "akka-stream" % "2.5.14",
  "com.typesafe.akka" %% "akka-actor"  % "2.5.14"
)
scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-language:higherKinds"
)
