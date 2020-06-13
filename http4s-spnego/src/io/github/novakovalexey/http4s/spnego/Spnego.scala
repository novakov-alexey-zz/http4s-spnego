package io.github.novakovalexey.http4s.spnego

import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.github.novakovalexey.http4s.spnego.SpnegoAuthenticator._
import org.http4s._
import org.http4s.server.AuthMiddleware

import scala.io.Codec

object Spnego {

  def apply[F[_]: Sync](cfg: SpnegoConfig): Spnego[F] =
    new Spnego[F](cfg)
}

class Spnego[F[_]](cfg: SpnegoConfig)(implicit F: Sync[F]) {
  implicit lazy val logger: Logger[F] = Slf4jLogger.getLogger[F]

  logger.info(s"Configuration:\n ${cfg.show}")

  private val secret = Codec.toUTF8(cfg.signatureSecret)
  private[spnego] lazy val tokens = new Tokens(cfg.tokenValidity.toMillis, secret)
  private[spnego] val authenticator = new SpnegoAuthenticator[F](cfg, tokens)

  val authToken: Kleisli[F, Request[F], Either[Rejection, Token]] =
    Kleisli(request => authenticator.apply(request.headers))

  private val onFailure: AuthedRoutes[Rejection, F] =
    Kleisli { req =>
      val result: F[(String, Seq[Header])] = req.context match {
        case AuthenticationFailedRejection(r, h) => (reasonToString(r), Seq(h)).pure[F]
        case MalformedHeaderRejection(name, msg, cause) =>
          for {
            c <- cause.pure[F]
            _ <- c.map(t => logger.error(t)("MalformedHeaderRejection")).getOrElse(F.unit)
          } yield (s"Failed to parse '$name' value, because of $msg", Seq.empty[Header])
        case ServerErrorRejection(e) => (s"server error: ${e.getMessage}", Seq.empty[Header]).pure[F]
        case UnexpectedErrorRejection(e) => (s"unexpected error: ${e.getMessage}", Seq.empty[Header]).pure[F]
      }

      OptionT.liftF(for {
        (entity, headers) <- result
        res = Response[F](Status.Unauthorized).putHeaders(headers: _*).withEntity(entity)
      } yield res)
    }

  def apply(service: AuthedRoutes[Token, F]): HttpRoutes[F] =
    middleware.apply(service)

  def middleware(onFailure: AuthedRoutes[Rejection, F]): AuthMiddleware[F, Token] =
    AuthMiddleware(authToken, onFailure)

  lazy val middleware: AuthMiddleware[F, Token] = AuthMiddleware(authToken, onFailure)

  def signCookie(token: Token): ResponseCookie = {
    val content = tokens.serialize(token)
    ResponseCookie(cfg.cookieName, content, domain = cfg.domain, path = cfg.path)
  }
}
