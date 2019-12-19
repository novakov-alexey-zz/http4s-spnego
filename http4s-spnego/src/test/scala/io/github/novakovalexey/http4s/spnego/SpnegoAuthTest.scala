package io.github.novakovalexey.http4s.spnego

import cats.data.{Kleisli, OptionT}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.http4s._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Codec

class SpnegoAuthTest extends AnyFlatSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

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
    Some(JaasConfig(principal, keytab, debug, None))
  )
  val spnego = new Spnego[IO](cfg)
  val login = new LoginEndpoint[IO](spnego)

  val userPrincipal = "myprincipal"
  val testTokens = new Tokens(cfg.tokenValidity.toMillis, Codec.toUTF8(cfg.signatureSecret))

  it should "reject invalid authorization token" in {
    val req = Request[IO]().putHeaders(Authorization(Credentials.Token(SpnegoAuthenticator.Negotiate.ci, "test")))

    val route = login.routes.orNotFound
    val res = route.run(req)
    val actualResp = res.unsafeRunSync

    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should ===(SpnegoAuthenticator.reasonToString(CredentialsRejected))
  }

  it should "reject invalid cookie" in {
    val req = Request[IO]().addCookie(cookieName, "myprincipal&1566120533815&0zjbRRVXDFlDYfRurlxaySKWhgE=")

    val route = login.routes.orNotFound
    val res = route.run(req)
    val actualResp = res.unsafeRunSync

    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should include("Failed to parse ")
  }

  def mockKerberos(token: Option[Token], tokens: Tokens = testTokens): Spnego[IO] = {
    new Spnego[IO](cfg) {
      override val tokens: Tokens = testTokens
      override val authenticator: SpnegoAuthenticator = new SpnegoAuthenticator(cfg, tokens) {
        override private[spnego] def kerberosAcceptToken(clientToken: Array[Byte]) = {
          (None, token)
        }
      }
    }
  }

  it should "reject token if Kerberos failed" in {
    //given
    val route = loginRoute(None)
    val clientToken = "test"
    val req2 = Request[IO]().putHeaders(Header("Authorization", s"${SpnegoAuthenticator.Negotiate} $clientToken"))
    //when
    val res2 = route.run(req2)
    val actualResp = res2.unsafeRunSync
    //then
    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should ===(SpnegoAuthenticator.reasonToString(CredentialsMissing))
  }

  it should "reject invalid number of fields in cookie token" in {
    val route = loginRoute(None)

    //given
    val req = Request[IO]().addCookie(RequestCookie(cookieName, s"$userPrincipal&"))
    //when
    val res = route.run(req)
    //then
    val actualResp = res.unsafeRunSync()
    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should include("incorrect number of fields")
  }

  it should "reject invalid expiration parameter in cookie token" in {
    val route = loginRoute(None)

    //given
    val req = Request[IO]().addCookie(RequestCookie(cookieName, s"$userPrincipal&a&b"))
    //when
    val res = route.run(req)
    //then
    val actualResp = res.unsafeRunSync()
    actualResp.status should ===(Status.Unauthorized)
    actualResp.as[String].unsafeRunSync() should include("expiration not a long")
  }

  it should "return authenticated token" in {
    //given
    val route = loginRoute(Some(testTokens.create(userPrincipal)))
    val initReq = Request[IO]()
    //when
    val res1 = route.run(initReq)
    //then
    res1.unsafeRunSync().status should ===(Status.Unauthorized)

    //given
    val clientToken = "test"
    val req2 = Request[IO]().putHeaders(Header("Authorization", s"${SpnegoAuthenticator.Negotiate} $clientToken"))
    //when
    val okResponse = route.run(req2).unsafeRunSync

    //then
    okResponse.status should ===(Status.Ok)
    okResponse.cookies.length should ===(1)
    okResponse.cookies.headOption.map(_.name should ===(cookieName)).getOrElse(fail())

    val cookie = okResponse.cookies.headOption.map(_.content).getOrElse(fail())
    val token = testTokens.parse(cookie)
    token.isRight should ===(true)
    token.getOrElse(fail()).principal should ===(userPrincipal)
    token.getOrElse(fail()).expiration should be > System.currentTimeMillis()

    //given
    val req3 = Request[IO]().addCookie(RequestCookie(cookieName, cookie))
    //when
    val res3 = route.run(req3).unsafeRunSync()
    //then
    res3.status should ===(Status.Ok)
  }

  it should "reject expired token inside the cookie" in {
    //given
    val noTokenValidity = new Tokens(0.millisecond.toMillis, Codec.toUTF8(cfg.signatureSecret))
    val routes = loginRoute(Some(noTokenValidity.create(userPrincipal)), noTokenValidity)
    val signature = "test"
    val req = Request[IO]().putHeaders(Header("Authorization", s"${SpnegoAuthenticator.Negotiate} $signature"))
    //when
    val io = routes.run(req)
    val resp = io.unsafeRunSync
    //then
    resp.status should ===(Status.Ok)
    val cookie = resp.cookies.headOption.map(_.content).getOrElse(fail())
    val token = noTokenValidity.parse(cookie)
    token.isRight should ===(true)

    //given
    val req3 = Request[IO]().addCookie(RequestCookie(cookieName, cookie))
    //when
    val res3 = routes.run(req3)
    //then
    res3.unsafeRunSync().status should ===(Status.Unauthorized)
  }

  def loginRoute(token: Option[Token], tokens: Tokens = testTokens): Kleisli[IO, Request[IO], Response[IO]] = {
    val authentication = mockKerberos(token)
    val login = new LoginEndpoint[IO](authentication)
    login.routes.orNotFound
  }

  it should "allow custom onFailure hander" in {
    val login = new LoginEndpoint[IO](spnego)
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
    maybeHeader.map(_.value should ===("test 2")).getOrElse(fail())
    actualResp.as[String].unsafeRunSync() should ===("test entity")
  }
}
