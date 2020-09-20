package io.github.novakovalexey.http4s.spnego

import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.{AuthedRoutes, HttpRoutes}

import scala.concurrent.duration._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    stream[IO].compile.drain.as(ExitCode.Success)

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] = for {
    spnego <- Stream.eval(makeSpnego)

    httpApp = new LoginEndpoint[F](spnego).routes.orNotFound
    finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

    stream <- BlazeServerBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(finalHttpApp)
      .serve
  } yield stream

  private def makeSpnego[F[_] : ConcurrentEffect : ContextShift : Timer] = {
    val realm = sys.env.getOrElse("REALM", "EXAMPLE.ORG")
    val principal = sys.env.getOrElse("PRINCIPAL", s"HTTP/testserver@$realm")
    val keytab = sys.env.getOrElse("KEYTAB", "/tmp/krb5.keytab")
    val debug = true
    val domain = None
    val path: Option[String] = None
    val tokenValidity = 3600.seconds
    val cookieName = "http4s.spnego"
    val signatureSecret = "secret"

    val cfg = SpnegoConfig(realm, principal, signatureSecret, domain, path, tokenValidity, cookieName)
    System.setProperty("java.security.auth.login.config", "test-server/src/main/resources/server-jaas.conf")
    Spnego[F](cfg)
  }
}

class LoginEndpoint[F[_]: Sync](spnego: Spnego[F]) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] =
    spnego(AuthedRoutes.of[Token, F] {
      case GET -> Root as token =>
        Ok(s"This page is protected using HTTP SPNEGO authentication; logged as ${token.principal}")
          .map(_.addCookie(spnego.signCookie(token)))
    })
}
