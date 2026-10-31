name := "udemy-scala-beginners"

version := "0.1"

scalaVersion := "3.3.1"

val http4sVersion = "0.23.30"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "io.circe" %% "circe-generic" % "0.14.10",
  "org.typelevel" %% "cats-effect" % "3.5.7",
  "org.scalameta" %% "munit" % "1.0.2" % Test
)
