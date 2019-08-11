# http4s-spnego
This library provides [SPNEGO Authentication](https://en.wikipedia.org/wiki/SPNEGO) as a middleware for [http4s](https://github.com/http4s/http4s).

Project is an adaptation of [akka-http-spnego](https://github.com/tresata/akka-http-spnego), but for http4s.

# How to use

Wrap AuthedRoutes with SpnegoAuthentication#middleware, so that you can get an instance of SPNEGO token. 
Wrapped routes will be called successfully *only if* SPNEGO authentication succeded. 

```scala
import cats.effect.Sync
import cats.implicits._
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.dsl.Http4sDsl

class LoginEndpoint[F[_]](spnego: SpnegoAuthentication[F])(implicit F: Sync[F]) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] =
    spnego.middleware(AuthedRoutes.of[Token, F] {
      case GET -> Root as token =>
        Ok(s"This page is protected using HTTP SPNEGO authentication; logged in as $token")
          .map(_.addCookie(spnego.makeCookie(token)))
    })
}
```

See [tests](http4s-spnego/src/test/scala/io/github/novakovalexey/http4s/spnego) and [test-server](test-server/src/main/scala/io/github/novakovalexey/http4s/spnego/Main.scala) module for more examples.
