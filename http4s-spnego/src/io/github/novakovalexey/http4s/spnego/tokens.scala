package io.github.novakovalexey.http4s.spnego

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

import io.github.novakovalexey.http4s.spnego.Tokens.{wrongFieldsError, wrongTypeError}

import scala.util.{Success, Try}

sealed abstract class TokenError(val message: String)
case class TokenParseError(override val message: String) extends TokenError(message)

private[spnego] object Tokens {
  val wrongTypeError = "expiration field type is not long"
  val wrongFieldsError = "wrong number of fields"
}

class Tokens(tokenValidity: Long, signatureSecret: Array[Byte]) {
  private def newExpiration: Long = System.currentTimeMillis + tokenValidity

  private[spnego] def sign(token: AuthToken): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(token.principal.getBytes(UTF_8))
    md.update(token.attributes.getBytes(UTF_8))
    val bb = ByteBuffer.allocate(8)
    bb.putLong(token.expiration)
    md.update(bb.array)
    md.update(signatureSecret)
    Base64Util.encode(md.digest)
  }

  def create(principal: String): AuthToken =
    AuthToken(principal, newExpiration)

  def parse(tokenString: String): Either[TokenError, AuthToken] =
    tokenString.split("&").toList match {
      case principal :: expirationString :: attributes :: signature :: Nil =>
        Try(expirationString.toLong) match {
          case Success(expiration) =>
            val token = AuthToken(principal, expiration, attributes)
            Either.cond(sign(token) == signature, token, TokenParseError("incorrect signature"))
          case _ => Left(TokenParseError(wrongTypeError))
        }
      case _ => Left(TokenParseError(wrongFieldsError))
    }

  def serialize(token: AuthToken): String =
    List(token.principal, token.expiration, token.attributes, sign(token)).mkString("&")
}

case class AuthToken private[spnego](principal: String, expiration: Long, attributes: String = "") {
  def expired: Boolean = System.currentTimeMillis > expiration
}
