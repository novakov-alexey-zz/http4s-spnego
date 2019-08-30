import Dependencies._
import sbtrelease.ReleaseStateTransformations._

useGpg := true
pgpReadOnly := false

ThisBuild /  publishTo := {
  val nexus = "https://oss.sonatype.org/"
  Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
}

ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credential")
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/novakov-alexey/http4s-spnego"),
    "scm:git:git@github.com:novakov-alexey/http4s-spnego.git"
  )
)
ThisBuild / developers := List(
  Developer(id = "novakov-alexey", name = "Alexey Novakov", email = "novakov.alex@gmail.com", url = url("https://github.com/novakov-alexey"))
)
ThisBuild / description := "Kerberos SPNEGO Authentification as HTTP4s middleware"
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/novakov-alexey/http4s-spnego"))
// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ =>
  false
}
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots".at(nexus + "content/repositories/snapshots"))
  else Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
}
ThisBuild / publishMavenStyle := true
ThisBuild / organization := "io.github.novakov-alexey"

lazy val sharedSettings = Seq(
  // https://github.com/sbt/sbt-pgp/issues/150
  updateOptions := updateOptions.value.withGigahorse(false),
  scalaVersion := "2.13.0",
  publishArtifact in Test := false,
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:higherKinds",
    "-language:postfixOps",
    "-feature",
    "-language:implicitConversions"
  )
)

releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq.empty[ReleaseStep]
releaseProcess ++= (if (sys.env.contains("RELEASE_VERSION_BUMP"))
  Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts
  )
else Seq.empty[ReleaseStep])
releaseProcess ++= (if (sys.env.contains("RELEASE_PUBLISH"))
  Seq[ReleaseStep](inquireVersions, setNextVersion, commitNextVersion, pushChanges)
else Seq.empty[ReleaseStep])

lazy val root = project
  .in(file("."))
  .aggregate(`http4s-spnego`, `test-server`)
  .settings(publishArtifact := false)

lazy val `http4s-spnego` = project.settings(
  sharedSettings,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  libraryDependencies ++= Seq(
    http4sCore,
    http4sBlaze,
    scalaLogging,
    //Test
    http4sDsl % Test,
    scalaTest,
    logback % Test
  )
)

lazy val `test-server` = project
  .settings(
    sharedSettings,
    libraryDependencies ++= Seq(http4sDsl, scalaLogging, logback),
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    dockerExposedPorts ++= Seq(8080)
  )
  .dependsOn(`http4s-spnego`)
  .enablePlugins(JavaAppPackaging)
