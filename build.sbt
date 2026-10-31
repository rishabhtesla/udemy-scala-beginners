val http4sVersion = "0.23.30"

lazy val root = (project in file(".")).settings(
  name := "udemy-scala-beginners",
  version := "0.1",
  scalaVersion := "3.3.1",
  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "io.circe" %% "circe-generic" % "0.14.10",
    "org.typelevel" %% "cats-effect" % "3.5.7",
    "org.scalameta" %% "munit" % "1.0.2" % Test
  )
)

// Separate classpaths keep Scala binary versions and framework dependencies apart.
// No aggregation: the original `sbt compile`, `test`, and `runMain` stay lightweight.
lazy val frameworkSettings = Seq(
  version := "0.1",
  Compile / run / fork := true,
  Test / fork := true,
  Test / parallelExecution := false,
  libraryDependencies += "org.scalameta" %% "munit" % "1.0.2" % Test
)

lazy val akka = (project in file("modules/akka")).settings(frameworkSettings).settings(
  name := "scala-akka-learning",
  scalaVersion := "2.13.12",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % "2.6.21",
    "com.typesafe.akka" %% "akka-stream" % "2.6.21",
    "com.typesafe.akka" %% "akka-persistence-typed" % "2.6.21",
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.21" % Test,
    "org.slf4j" % "slf4j-simple" % "1.7.36"
  )
)

lazy val spark = (project in file("modules/spark")).settings(frameworkSettings).settings(
  name := "scala-spark-learning",
  scalaVersion := "2.12.18",
  libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.5.7",
  // Spark's launcher normally supplies these JDK module options; sbt does not.
  javaOptions ++= Seq(
    "-Xmx1g",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
    "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
  )
)

lazy val flink = (project in file("modules/flink")).settings(frameworkSettings).settings(
  name := "scala-flink-learning",
  scalaVersion := "2.13.12",
  // Use Flink's supported Java DataStream API from Scala, not its deprecated Scala API.
  libraryDependencies ++= Seq(
    "org.apache.flink" % "flink-streaming-java" % "1.20.2",
    "org.apache.flink" % "flink-clients" % "1.20.2",
    "org.slf4j" % "slf4j-simple" % "1.7.36"
  ),
  javaOptions ++= Seq(
    "-Xmx1g",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED"
  )
)
