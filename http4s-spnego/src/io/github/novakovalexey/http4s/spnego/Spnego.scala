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

  def apply[F[_]: Sync](cfg: SpnegoConfig): F[Spnego[F]] = {
    implicit lazy val logger: Logger[F] = Slf4jLogger.getLogger[F]
    for {
      _ <- logger.info(s"Configuration:\n ${cfg.show}")
      secret = Codec.toUTF8(cfg.signatureSecret)
      tokens = new Tokens(cfg.tokenValidity.toMillis, secret)
      authenticator <- SpnegoAuthenticator[F](cfg, tokens)
    } yield new Spnego[F](cfg, tokens, authenticator)

  }
}

class Spnego[F[_]: Sync](cfg: SpnegoConfig, tokens: Tokens, authenticator: SpnegoAuthenticator[F])(implicit
  logger: Logger[F]
) {
  val authToken: Kleisli[F, Request[F], Either[Rejection, AuthToken]] =
    Kleisli(request => authenticator.apply(request.headers))

  private val onFailure: AuthedRoutes[Rejection, F] =
    Kleisli { req =>
      val rejection = req.context match {
        case AuthenticationFailedRejection(r, h) => (reasonToString(r), List(h)).pure[F]
        case MalformedHeaderRejection(name, msg, cause) =>
          cause.fold(Sync[F].unit)(c => logger.error(c)("MalformedHeaderRejection")) *> (
            s"Failed to parse '$name' value, because of $msg",
            Nil
          ).pure[F]
        case ServerErrorRejection(e) => (s"server error: ${e.getMessage}", Nil).pure[F]
        case UnexpectedErrorRejection(e) => (s"unexpected error: ${e.getMessage}", Nil).pure[F]
      }

      OptionT.liftF(for {
        (msg, headers) <- rejection
        res = Response[F](Status.Unauthorized).putHeaders(headers: _*).withEntity(msg)
      } yield res)
    }

  def apply(service: AuthedRoutes[AuthToken, F]): HttpRoutes[F] =
    middleware(service)

  def middleware(onFailure: AuthedRoutes[Rejection, F]): AuthMiddleware[F, AuthToken] =
    AuthMiddleware(authToken, onFailure)

  lazy val middleware: AuthMiddleware[F, AuthToken] = AuthMiddleware(authToken, onFailure)

  def signCookie(token: AuthToken): ResponseCookie = {
    val content = tokens.serialize(token)
    ResponseCookie(cfg.cookieName, content, domain = cfg.domain, path = cfg.path)
  }
}
