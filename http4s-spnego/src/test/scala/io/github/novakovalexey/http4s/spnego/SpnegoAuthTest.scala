package io.github.novakovalexey.http4s.spnego

import cats.implicits._
import cats.data.{Kleisli, OptionT}
import cats.effect.{ContextShift, IO}
import org.http4s._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Codec

class SpnegoAuthTest extends FlatSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val realm = "EXAMPLE.ORG"
  val principal = s"HTTP/myservice@$realm"
  val keytab = "/etc/krb5.keytab"
  val debug = true
  val domain = Some("myservice")
  val path: Option[String] = None
  val tokenValidity: FiniteDuration = 3600.seconds
  val cookieName = "http4s.spnego"

  val cfg = SpnegoConfig(principal, realm, keytab, debug, None, "secret", domain, path, cookieName, tokenValidity)
  val authentication = new SpnegoAuthentication[IO](cfg)
  val login = new LoginEndpoint[IO](authentication)

  val userPrincipal = "myprincipal"
  val testTokens = new Tokens(cfg.tokenValidity.toMillis, Codec.toUTF8(cfg.signatureSecret))

  it should "reject invalid authorization token" in {
    val req = Request[IO]().putHeaders(Authorization(Credentials.Token(SpnegoAuthenticator.Negotiate.ci, "test")))

    val routes = login.routes.orNotFound
    val res = routes.run(req)
    val actualResp = res.unsafeRunSync

    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should ===(SpnegoAuthenticator.reasonToString(CredentialsRejected))
  }

  it should "reject invalid cookie" in {
    val req = Request[IO]().addCookie(cookieName, "myprincipal&1566120533815&0zjbRRVXDFlDYfRurlxaySKWhgE=")

    val routes = login.routes.orNotFound
    val res = routes.run(req)
    val actualResp = res.unsafeRunSync

    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should include("Failed to parse ")
  }

  def mockKerberos(token: Option[Token]): SpnegoAuthentication[IO] = {
    new SpnegoAuthentication[IO](cfg) {
      override val tokens: Tokens = testTokens
      override val authenticator: SpnegoAuthenticator = new SpnegoAuthenticator(cfg, tokens) {
        override private[spnego] def kerberosAcceptToken(clientToken: Array[Byte]) = {
          (None, token)
        }
      }
    }
  }

  it should "reject token if Kerberos failed" in {
    val authentication = mockKerberos(None)
    val login = new LoginEndpoint[IO](authentication)
    val routes = login.routes.orNotFound

    //given
    val clientToken = "test"
    val req2 = Request[IO]().putHeaders(Header("Authorization", s"${SpnegoAuthenticator.Negotiate} $clientToken"))
    //when
    val res2 = routes.run(req2)
    val actualResp = res2.unsafeRunSync
    //then
    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should ===(SpnegoAuthenticator.reasonToString(CredentialsMissing))
  }

  it should "reject invalid number of fields in cookie token" in {
    val login = new LoginEndpoint[IO](authentication)
    val routes = login.routes.orNotFound

    //given
    val req = Request[IO]().addCookie(RequestCookie(cookieName, s"$userPrincipal&"))
    //when
    val res = routes.run(req)
    //then
    val actualResp = res.unsafeRunSync()
    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should include("incorrect number of fields")
  }

  it should "reject invalid expiration parameter in cookie token" in {
    val login = new LoginEndpoint[IO](authentication)
    val routes = login.routes.orNotFound

    //given
    val req = Request[IO]().addCookie(RequestCookie(cookieName, s"$userPrincipal&a&b"))
    //when
    val res = routes.run(req)
    //then
    val actualResp = res.unsafeRunSync()
    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should include("expiration not a long")
  }

  it should "return authenticated token" in {
    val authentication = mockKerberos(Some(testTokens.create(userPrincipal)))

    val login = new LoginEndpoint[IO](authentication)
    val routes = login.routes.orNotFound

    //given
    val initReq = Request[IO]()
    //when
    val res1 = routes.run(initReq)
    //then
    res1.unsafeRunSync().status should ===(Status.Unauthorized)

    //given
    val clientToken = "test"
    val req2 = Request[IO]().putHeaders(Header("Authorization", s"${SpnegoAuthenticator.Negotiate} $clientToken"))
    //when
    val res2 = routes.run(req2)
    val okResponse = res2.unsafeRunSync
    //then
    okResponse.status should ===(Status.Ok)
    okResponse.cookies.length should ===(1)
    okResponse.cookies.head.name should ===(cookieName)

    val token = testTokens.parse(okResponse.cookies.head.content)
    token.principal should ===(userPrincipal)
    token.expiration should be > System.currentTimeMillis()

    //given
    val req3 = Request[IO]().addCookie(RequestCookie(cookieName, okResponse.cookies.head.content))
    //when
    val res3 = routes.run(req3)
    //then
    res3.unsafeRunSync().status should ===(Status.Ok)
  }

  it should "use onFailure function" in {
    val login = new LoginEndpoint[IO](authentication)
    val onFailure: AuthedRoutes[Rejection, IO] = Kleisli { _ =>
      val res = Response[IO](Status.BadRequest).putHeaders(Header("test 1", "test 2")).withEntity("test entity")
      OptionT.liftF(res.pure[IO])
    }
    val routes = login.routes(onFailure).orNotFound

    //given
    val initReq = Request[IO]()
    //when
    val res1 = routes.run(initReq)
    //then
    val actualResp = res1.unsafeRunSync()
    actualResp.status should ===(Status.BadRequest)
    val maybeHeader = actualResp.headers.get("test 1".ci)
    maybeHeader.isDefined should ===(true)
    maybeHeader.get.value should ===("test 2")
    actualResp.as[String].unsafeRunSync() should ===("test entity")
  }
}
