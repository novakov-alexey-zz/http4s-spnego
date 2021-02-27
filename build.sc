import $ivy.`com.goyeau::mill-git:9977203`
import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`
import $ivy.`io.github.davidgregory084::mill-tpolecat:0.1.4`
import $file.project.Dependencies, Dependencies.Dependencies._
import com.goyeau.mill.git.GitVersionedPublishModule
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import mill.scalalib.scalafmt.ScalafmtModule
import mill.modules.Jvm
import ammonite.ops._

object ScalaVersion {
  val ver213 = "2.13.5"
  val ver212 = "2.12.13"
}

object `http4s-spnego` extends Cross[Http4sSpnegoModule](ScalaVersion.ver213, ScalaVersion.ver212)
class Http4sSpnegoModule(val crossScalaVersion: String)
    extends CrossScalaModule
    with TpolecatModule
    with ScalafmtModule    
    with GitVersionedPublishModule {
  override def scalacOptions =
    super.scalacOptions().filter(_ != "-Wunused:imports").filter(_ != "-Wunused:explicits") ++
      (if (scalaVersion().startsWith("2.12")) Seq("-Ypartial-unification") else Seq.empty)
  override def ivyDeps =
    super.ivyDeps() ++ http4sBase ++ logging ++ kindProjector
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ betterMonadicFor

  object test extends Tests {
    def testFrameworks = Seq("org.scalatest.tools.Framework")
    override def ivyDeps = super.ivyDeps() ++ tests
    override def scalacOptions =
      super.scalacOptions().filter(_ != "-Wunused:params").filter(_ != "-Xfatal-warnings") ++
        (if (scalaVersion().startsWith("2.12")) Seq("-Ypartial-unification") else Seq.empty)
    def testOne(args: String*) = T.command {
      super.runMain("org.scalatest.run", args: _*)
    }
  }

  override def artifactName = "http4s-spnego"
  def pomSettings =
    PomSettings(
      description = "Kerberos SPNEGO Authentification as HTTP4s middleware",
      organization = "io.github.novakov-alexey",
      url = "https://github.com/novakov-alexey/http4s-spnego",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("novakov-alexey", "http4s-spnego"),
      developers = Seq(Developer("novakov-alexey", "Alexey Novakov", "https://github.com/novakov-alexey"))
    )
}
object `test-server` extends ScalaModule {
  def scalaVersion = ScalaVersion.ver213
  override def ivyDeps =
    super.ivyDeps() ++ http4sBase ++ http4sDsl
  override def moduleDeps =
    super.moduleDeps ++ Seq(`http4s-spnego`(ScalaVersion.ver213))
  def packageIt = T {
    val dest = T.ctx().dest
    val libDir = dest / 'lib
    val binDir = dest / 'bin

    mkdir(libDir)
    mkdir(binDir)

    val allJars = packageSelfModules() ++ runClasspath()
      .map(_.path)
      .filter(path => exists(path) && !path.isDir)
      .toSeq

    allJars.foreach { file =>
      cp.into(file, libDir)
    }

    val runnerFile = Jvm.createLauncher(finalMainClass(), Agg.from(ls(libDir)), forkArgs())

    mv.into(runnerFile.path, binDir)

    PathRef(dest)
  }

  // package root and dependent modules with meaningful names
  def packageSelfModules = T {
    T.traverse(moduleDeps :+ this) { module =>
      module.jar
        .zip(module.artifactName)
        .zip(module.artifactId)
        .map { case ((jar, name), suffix) =>
          val namedJar = jar.path / up / s"$name$suffix.jar"
          cp(jar.path, namedJar)

          namedJar
        }
    }
  }
}
