# http4s-spnego
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.novakov-alexey/http4s-spnego_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.novakov-alexey/http4s-spnego_2.13)
[![Build Status](https://travis-ci.org/novakov-alexey/http4s-spnego.svg?branch=master)](https://travis-ci.org/novakov-alexey/http4s-spnego)

This library provides [SPNEGO Authentication](https://en.wikipedia.org/wiki/SPNEGO) as a middleware for [http4s](https://github.com/http4s/http4s).

Project is an adaptation of [akka-http-spnego](https://github.com/tresata/akka-http-spnego), but for http4s.

# How to use

0. Add library into your dependencies:

```scala
libraryDependencies += "io.github.novakov-alexey" % "http4s-spnego_2.13" % "<version>"
or 
libraryDependencies += "io.github.novakov-alexey" % "http4s-spnego_2.12" % "<version>"
```

1. Instantiate `SpnegoAuthentication` using `SpnegoConfig` case class, for example:
```scala
val realm = "EXAMPLE.ORG"
val principal = s"HTTP/myservice@$realm"
val keytab = "/etc/krb5.keytab"
val debug = true
val domain = Some("myservice")
val path: Option[String] = None
val tokenValidity: FiniteDuration = 3600.seconds
val cookieName = "http4s.spnego"

val cfg = SpnegoConfig(principal, realm, keytab, debug, None, "secret", domain, path, tokenValidity, cookieName)
val authentication = new SpnegoAuthentication[IO](cfg)
``` 
2. Wrap AuthedRoutes with SpnegoAuthentication#middleware, so that you can get an instance of SPNEGO token. 
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

3. Use routes in your server:
```scala
val login = new LoginEndpoint[IO](authentication)
val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(login)

BlazeServerBuilder[F]
  .bindHttp(8080, "0.0.0.0")
  .withHttpApp(finalHttpApp)
  .serve
```

See [tests](http4s-spnego/src/test/scala/io/github/novakovalexey/http4s/spnego) and [test-server](test-server/src/main/scala/io/github/novakovalexey/http4s/spnego/Main.scala) module for more examples.

# Testing with testserver

1. Make sure Kerberos is installed and configured for your server and client machines.
2. Configure test server with proper realm, principal, keytab path (see config above)
3. Authenticated client via `kinit` CLI tool to the same realm used for the server side
4. Start test server: `sbt 'project test-server' run`
4. Use `curl` or Web-Browser to initiate a negotiation request (google for that or try this [link](http://www.microhowto.info/howto/configure_firefox_to_authenticate_using_spnego_and_kerberos.html)). Using curl: 
```bash
curl -k --negotiate -u : -b ~/cookiejar.txt -c ~/cookiejar.txt http://<yourserver>:8080/
```

