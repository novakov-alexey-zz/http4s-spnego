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

private[spnego] object SpnegoAuthenticator {
  val Negotiate = "Negotiate"
  val Authenticate = "WWW-Authenticate"

  def reasonToString: RejectionReason => String = {
    case CredentialsRejected => "Credentials rejected"
    case CredentialsMissing => "Credentials are missing"
  }

  private[spnego] def loginContext[F[_]](cfg: SpnegoConfig)(implicit F: Sync[F]): F[LoginContext] = for {
    (entryName, kerberosConfiguration) <- F.delay {
      cfg.jaasConfig match {
        case Some(c) =>
          val noEntryNeeded = ""
          (noEntryNeeded, KerberosConfiguration(cfg.principal, c).some)
        case None => (SpnegoConfig.JaasConfigEntryName, None)
      }
    }.onError { case e =>
      F.raiseError(new RuntimeException("Spnego Configuration creation has been failed", e))
    }

    lc <- F.delay {
      val subject = new Subject(
        false,
        Collections.singleton(new KerberosPrincipal(cfg.principal)),
        Collections.emptySet(),
        Collections.emptySet()
      )
      val noCallback = null
      new LoginContext(entryName, subject, noCallback, kerberosConfiguration.orNull)
    }.onError { case e =>
      F.raiseError(
        new RuntimeException(
          "Login context creation failed. In case the JAAS file is used, please check that 'java.security.auth.login.config' Java property is set",
          e
        )
      )
    }
  } yield lc

  /*
  Creates LoginContext, logins and created GSSManager based on SpnegoConfig
   */
  private[spnego] def apply[F[_]](cfg: SpnegoConfig, tokens: Tokens)(implicit F: Sync[F]): F[SpnegoAuthenticator[F]] =
    for {
      (lc, manager) <- login[F](cfg)
    } yield new SpnegoAuthenticator[F](cfg, tokens, lc, manager)

  private[spnego] def login[F[_]](cfg: SpnegoConfig)(implicit F: Sync[F]): F[(LoginContext, GSSManager)] =
    for {
      lc <- loginContext(cfg)
      _ <- F.delay(lc.login()).onError { case e =>
        F.raiseError(new RuntimeException("Service login failed", e))
      }
      manager <- createGssManager[F](lc).onError { case e =>
        F.raiseError(new RuntimeException("GSSManager creation failed", e))
      }
    } yield (lc, manager)

  private[spnego] def createGssManager[F[_]](lc: LoginContext)(implicit F: Sync[F]) =
    F.delay {
      Subject.doAs(
        lc.getSubject,
        new PrivilegedAction[GSSManager] {
          override def run: GSSManager = GSSManager.getInstance
        }
      )
    }
}

private[spnego] class SpnegoAuthenticator[F[_]](
  cfg: SpnegoConfig,
  tokens: Tokens,
  lc: LoginContext,
  gssManager: GSSManager
)(implicit F: Sync[F]) {
  implicit lazy val logger: Logger[F] = Slf4jLogger.getLogger[F]

  private[spnego] def apply(hs: Headers): F[Either[Rejection, AuthToken]] =
    cookieToken(hs).orElse(kerberosNegotiate(hs)).getOrElseF(initiateNegotiations)

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
            logger.debug("SPNEGO token inside cookie expired") *> none[Either[Rejection, AuthToken]].pure[F]
          else
            logger.debug("SPNEGO token inside cookie not expired") *> token.asRight[Rejection].some.pure[F]
        case Left(e) => Either.left[Rejection, AuthToken](e).some.pure[F]
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

  private def kerberosCore(clientToken: Array[Byte]): F[Either[Rejection, AuthToken]] =
    F.defer {
      for {
        (maybeServerToken, maybeToken) <- kerberosAcceptToken(clientToken)
        _ <- logger.debug(s"serverToken '${maybeServerToken.map(Base64Util.encode)}' token '$maybeToken'")
        token <- maybeToken match {
          case Some(t) => logger.debug("received new token") *> t.asRight[Rejection].pure[F]
          case _ =>
            logger.debug("no token received, but if there is a serverToken, then negotiations are ongoing") *> Either
              .left[Rejection, AuthToken](
                AuthenticationFailedRejection(CredentialsMissing, challengeHeader(maybeServerToken))
              )
              .pure[F]
        }
      } yield token
    }.recoverWith {
      case e: PrivilegedActionException =>
        e.getException match {
          case e: IOException =>
            logger.error(e)("server error") *> Either.left[Rejection, AuthToken](ServerErrorRejection(e)).pure[F]
          case _ =>
            logger.error(e)("negotiation failed") *> Either
              .left[Rejection, AuthToken](AuthenticationFailedRejection(CredentialsRejected, challengeHeader()))
              .pure[F]
        }
      case e =>
        logger.error(e)("unexpected error") *> Either.left[Rejection, AuthToken](UnexpectedErrorRejection(e)).pure[F]
    }

  private[spnego] def kerberosAcceptToken(clientToken: Array[Byte]): F[(Option[Array[Byte]], Option[AuthToken])] =
    F.delay {
      Subject.doAs(
        lc.getSubject,
        new PrivilegedExceptionAction[(Option[Array[Byte]], Option[AuthToken])] {
          override def run: (Option[Array[Byte]], Option[AuthToken]) = {
            val defaultAcceptor: GSSCredential = null
            val gssContext = gssManager.createContext(defaultAcceptor)
            val serverToken = Option(gssContext.acceptSecContext(clientToken, 0, clientToken.length))
            val authToken = if (gssContext.isEstablished) Some(tokens.create(gssContext.getSrcName.toString)) else None
            gssContext.dispose()
            (serverToken, authToken)
          }
        }
      )
    }

  private def initiateNegotiations: F[Either[Rejection, AuthToken]] =
    logger.debug("no negotiation header found, initiating negotiations") *>
      Either.left[Rejection, AuthToken](AuthenticationFailedRejection(CredentialsMissing, challengeHeader())).pure[F]
}
