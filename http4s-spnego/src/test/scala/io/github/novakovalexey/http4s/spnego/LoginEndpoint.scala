package io.github.novakovalexey.http4s.spnego

import cats.effect.Sync
import cats.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes}

class LoginEndpoint[F[_]: Sync](spnego: SpnegoAuthentication[F]) extends Http4sDsl[F] {

  private val authRoutes = AuthedRoutes.of[Token, F] {
    case GET -> Root as token =>
      Ok(s"This page is protected using HTTP SPNEGO authentication; logged in as $token")
        .map(_.addCookie(spnego.makeCookie(token)))
  }

  val routes: HttpRoutes[F] = spnego.middleware(authRoutes)

  def routes(onFailure: AuthedRoutes[Rejection, F]):  HttpRoutes[F] =
    spnego.middleware(onFailure)(authRoutes)
}
