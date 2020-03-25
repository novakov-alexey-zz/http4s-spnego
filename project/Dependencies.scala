import sbt._

object Dependencies {
  lazy val http4sVersion = "0.21.2"

  val http4sCore = "org.http4s" %% "http4s-core" % http4sVersion
  val http4sBlaze = "org.http4s" %% "http4s-blaze-server" % http4sVersion
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  val kindProjector = "org.typelevel" % "kind-projector" % "0.10.3"
  //Test
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion
  val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1" % Test
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
}
