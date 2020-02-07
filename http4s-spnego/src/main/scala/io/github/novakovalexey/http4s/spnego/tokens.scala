package io.github.novakovalexey.http4s.spnego

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

import scala.util.{Success, Try}

sealed abstract class TokenError(val message: String)
case class TokenParseError(override val message: String) extends TokenError(message)

class Tokens(tokenValidity: Long, signatureSecret: Array[Byte]) {
  private def newExpiration: Long = System.currentTimeMillis + tokenValidity

  private[spnego] def sign(token: Token): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(token.principal.getBytes(UTF_8))
    md.update(token.attributes.getBytes(UTF_8))
    val bb = ByteBuffer.allocate(8)
    bb.putLong(token.expiration)
    md.update(bb.array)
    md.update(signatureSecret)
    Base64Util.encode(md.digest)
  }

  def create(principal: String): Token =
    Token(principal, newExpiration)

  def parse(tokenString: String): Either[TokenError, Token] = tokenString.split("&").toList match {
    case principal :: expirationString :: attributes :: signature :: Nil =>
      Try(expirationString.toLong) match {
        case Success(expiration) =>
          val token = Token(principal, expiration, attributes)
          Either.cond(sign(token) == signature, token, TokenParseError("incorrect signature"))
        case _ => Left(TokenParseError("expiration not a long"))
      }
    case _ => Left(TokenParseError("incorrect number of fields"))
  }

  def serialize(token: Token): String =
    List(token.principal, token.expiration, token.attributes, sign(token)).mkString("&")
}

case class Token private[spnego] (principal: String, expiration: Long, attributes: String = "") {
  def expired: Boolean = System.currentTimeMillis > expiration
}
