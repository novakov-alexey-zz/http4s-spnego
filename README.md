# http4s-spnego

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e5cbdb15d6e14135bc970a5f83706fcb)](https://app.codacy.com/app/novakov.alex/http4s-spnego?utm_source=github.com&utm_medium=referral&utm_content=novakov-alexey/http4s-spnego&utm_campaign=Badge_Grade_Dashboard)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.novakov-alexey/http4s-spnego_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.novakov-alexey/http4s-spnego_2.13)
[![Build Status](https://travis-ci.org/novakov-alexey/http4s-spnego.svg?branch=master)](https://travis-ci.org/novakov-alexey/http4s-spnego)

This library provides [SPNEGO Authentication](https://en.wikipedia.org/wiki/SPNEGO) as a middleware for [http4s](https://github.com/http4s/http4s).

Project is an adaptation of [akka-http-spnego](https://github.com/tresata/akka-http-spnego), but for http4s.

## How to use

1.  Add library into your dependencies:

```scala
libraryDependencies += "io.github.novakov-alexey" % "http4s-spnego_2.13" % "<version>"
or 
libraryDependencies += "io.github.novakov-alexey" % "http4s-spnego_2.12" % "<version>"
```

2.  Instantiate `Spnego` using `SpnegoConfig` and `JaasConfig` case classes:

```scala
import io.github.novakovalexey.http4s.spnego.SpnegoConfig
import io.github.novakovalexey.http4s.spnego.JaasConfig

val realm = "EXAMPLE.ORG"
val principal = s"HTTP/myservice@$realm"
val keytab = "/etc/krb5.keytab"
val debug = true
val domain = Some("myservice")
val path: Option[String] = None
val tokenValidity: FiniteDuration = 3600.seconds
val cookieName = "http4s.spnego"
val cfg = SpnegoConfig(
    realm,
    principal,
    "secret",
    domain,
    path,
    tokenValidity,
    cookieName,
    Some(JaasConfig(keytab, debug, None)) // option 1
  )

val spnego = Spnego[IO](cfg)
```

JaasConfig can be also set to `None` value (option 2) in order pass JaasConfig via standard JAAS file. For example:
 
```scala
System.setProperty("java.security.auth.login.config", "test-server/src/main/resources/server-jaas.conf")
```

See example of standard JAAS file at `test-server/src/main/resources/server-jaas.conf`

3.  Wrap AuthedRoutes with spnego#middleware, so that you can get an instance of SPNEGO token. 
    Wrapped routes will be called successfully _only if_ SPNEGO authentication succeeded. 

```scala
import cats.effect.Sync
import cats.implicits._
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.dsl.Http4sDsl

class LoginEndpoint[F[_]: Sync](spnego: Spnego[F]) extends Http4sDsl[F] {

  val routes: HttpRoutes[F] =
    spnego(AuthedRoutes.of[Token, F] {
      case GET -> Root as token =>
        Ok(s"This page is protected using HTTP SPNEGO authentication; logged in as ${token.principal}")
          .map(_.addCookie(spnego.signCookie(token)))
    })
}
```

4.  Use routes in your server:

```scala
val login = new LoginEndpoint[IO](spnego)
val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(login)

BlazeServerBuilder[F]
  .bindHttp(8080, "0.0.0.0")
  .withHttpApp(finalHttpApp)
  .serve
```

## Add property to the Token

If you need to add more fields into JWT token, there is a special String field `Token.attributes`:

```scala
// this route is used to create cookie once SPNEGO is done
case GET -> Root as token =>
   val id = "groupId=1"
   Ok(s"logged in as ${token.principal}")
      .map(_.addCookie(spnego.signCookie(token.copy(attributes = id))))

// this route takes already authenticated user and its token 
case POST -> Root as token =>
   val id = token.attributes
   // do something with id
   Ok("processed")      
```

Added field will be used to create a JWT signature.

See [tests](http4s-spnego/src/test/scala/io/github/novakovalexey/http4s/spnego) and [test-server](test-server/src/main/scala/io/github/novakovalexey/http4s/spnego/Main.scala) module for more examples.

## Testing with test server

1.  Make sure Kerberos is installed and configured for your server and client machines.
2.  Configure test server with proper realm, principal, keytab path (see config above)
3.  Authenticate client via `kinit` CLI tool to the same realm used for the server side
4.  Start test server: `sbt 'project test-server' run`
5.  Use `curl` or Web-Browser to initiate a negotiation request (google for that or try this [link](http://www.microhowto.info/howto/configure_firefox_to_authenticate_using_spnego_and_kerberos.html)). In case you want to test with  `curl`, there is command for that: 

```bash
curl -k --negotiate -u : -b ~/cookiejar.txt -c ~/cookiejar.txt http://<yourserver>:8080/
```
