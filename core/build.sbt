libraryDependencies ++= Seq(
  "io.skuber"         %% "skuber"              % "2.0.12",
  "com.typesafe.akka" %% "akka-stream"         % "2.5.14",
  "com.typesafe.akka" %% "akka-actor"          % "2.5.14",
  "com.chuusai"       %% "shapeless"           % "2.3.3",

  "org.scalatest"     %% "scalatest"           % "3.0.5"  % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.14" % Test
)
scalacOptions ++= Seq(
  "-Ypartial-unification",
  "-language:higherKinds"
)
