package io.github.novakovalexey.http4s.spnego

import java.io.IOException
import java.security.{PrivilegedAction, PrivilegedActionException, PrivilegedExceptionAction}
import java.util.Collections

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.github.novakovalexey.http4s.spnego.SpnegoAuthenticator._
import javax.security.auth.Subject
import javax.security.auth.kerberos.KerberosPrincipal
import javax.security.auth.login.LoginContext
import org.http4s._
import org.ietf.jgss.{GSSCredential, GSSManager}

import scala.util.Try

private[spnego] object SpnegoAuthenticator {
  val Negotiate = "Negotiate"
  val Authenticate = "WWW-Authenticate"

  def reasonToString: RejectionReason => String = {
    case CredentialsRejected => "Credentials rejected"
    case CredentialsMissing => "Credentials are missing"
  }
}

private[spnego] class SpnegoAuthenticator[F[_]](cfg: SpnegoConfig, tokens: Tokens)(implicit F: Sync[F]) {
  implicit lazy val logger: Logger[F] = Slf4jLogger.getLogger[F]

  private val subject = new Subject(
    false,
    Collections.singleton(new KerberosPrincipal(cfg.principal)),
    Collections.emptySet(),
    Collections.emptySet()
  )

  private val (entryName, kerberosConfiguration) =
    cfg.jaasConfig match {
      case Some(c) => ("", KerberosConfiguration(cfg.principal, c).some)
      case None => (SpnegoConfig.JaasConfigEntryName, None)
    }

  private val noCallback = null
  private val loginContext =
    Try(new LoginContext(entryName, subject, noCallback, kerberosConfiguration.orNull)).fold(
      e =>
        throw new RuntimeException(
          "In case of JAAS file is used, please check that java.security.auth.login.config Java property is set",
          e
        ),
      identity
    )

  loginContext.login()

  private[spnego] def apply(hs: Headers): F[Either[Rejection, Token]] =
    cookieToken(hs).orElse(kerberosNegotiate(hs)).getOrElseF(initiateNegotiations)

  private val gssManager = Subject.doAs(
    loginContext.getSubject,
    new PrivilegedAction[GSSManager] {
      override def run: GSSManager = GSSManager.getInstance
    }
  )

  private def cookieToken(hs: Headers) =
    for {
      c <-
        headers.Cookie
          .from(hs)
          .collect { case h => h.values.find(_.name == cfg.cookieName) }
          .flatten
          .toOptionT[F]

      _ <- OptionT(logger.debug("cookie found").map(_.some))

      t <- Some(
        tokens
          .parse(c.content)
          .leftMap(e => MalformedHeaderRejection(s"Cookie: ${cfg.cookieName}", e.message, None))
      ).toOptionT[F]

      res <- OptionT(t match {
        case Right(token) =>
          if (token.expired)
            logger.debug("SPNEGO token inside cookie expired") *> none[Either[Rejection, Token]].pure[F]
          else
            logger.debug("SPNEGO token inside cookie not expired") *> token.asRight[Rejection].some.pure[F]
        case Left(e) => Either.left[Rejection, Token](e).some.pure[F]
      })
    } yield res

  private def clientToken(hs: Headers): F[Option[Array[Byte]]] =
    for {
      authHeader <- headers.Authorization.from(hs).filter(_.value.startsWith(Negotiate)).pure[F]
      token <- authHeader match {
        case Some(header) =>
          logger.debug("authorization header found") *> Base64Util
                .decode(header.value.substring(Negotiate.length).trim)
                .some
                .pure[F]
        case _ => Option.empty[Array[Byte]].pure[F]
      }
    } yield token

  private def kerberosNegotiate(hs: Headers) =
    for {
      token <- OptionT(clientToken(hs))
      result <- OptionT(kerberosCore(token).map(Option(_)))
    } yield result

  private def challengeHeader(maybeServerToken: Option[Array[Byte]] = None): Header = {
    val scheme = Negotiate + maybeServerToken.map(" " + Base64Util.encode(_)).getOrElse("")
    Header(Authenticate, scheme)
  }

  private def kerberosCore(clientToken: Array[Byte]): F[Either[Rejection, Token]] =
    F.defer {
      for {
        (maybeServerToken, maybeToken) <- kerberosAcceptToken(clientToken)
        _ <- logger.debug(s"serverToken '${maybeServerToken.map(Base64Util.encode)}' token '$maybeToken'")
        token <- maybeToken match {
          case Some(t) => logger.debug("received new token") *> t.asRight[Rejection].pure[F]
          case _ =>
            logger.debug("no token received, but if there is a serverToken, then negotiations are ongoing") *> Either
                  .left[Rejection, Token](
                    AuthenticationFailedRejection(CredentialsMissing, challengeHeader(maybeServerToken))
                  )
                  .pure[F]
        }
      } yield token
    }.recoverWith {
      case e: PrivilegedActionException =>
        e.getException match {
          case e: IOException =>
            logger.error(e)("server error") *> Either.left[Rejection, Token](ServerErrorRejection(e)).pure[F]
          case _ =>
            logger.error(e)("negotiation failed") *> Either
                  .left[Rejection, Token](AuthenticationFailedRejection(CredentialsRejected, challengeHeader()))
                  .pure[F]
        }
      case e =>
        logger.error(e)("unexpected error") *> Either.left[Rejection, Token](UnexpectedErrorRejection(e)).pure[F]
    }

  private[spnego] def kerberosAcceptToken(clientToken: Array[Byte]): F[(Option[Array[Byte]], Option[Token])] =
    F.delay {
      Subject.doAs(
        loginContext.getSubject,
        new PrivilegedExceptionAction[(Option[Array[Byte]], Option[Token])] {
          override def run: (Option[Array[Byte]], Option[Token]) = {
            val defaultAcceptor: GSSCredential = null
            val gssContext = gssManager.createContext(defaultAcceptor)
            val res = (
              Option(gssContext.acceptSecContext(clientToken, 0, clientToken.length)),
              if (gssContext.isEstablished) Some(tokens.create(gssContext.getSrcName.toString)) else None
            )
            gssContext.dispose()
            res
          }
        }
      )
    }

  private def initiateNegotiations: F[Either[Rejection, Token]] =
    logger.debug("no negotiation header found, initiating negotiations") *>
        Either.left[Rejection, Token](AuthenticationFailedRejection(CredentialsMissing, challengeHeader())).pure[F]
}
