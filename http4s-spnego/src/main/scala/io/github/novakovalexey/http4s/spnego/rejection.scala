package io.github.novakovalexey.http4s.spnego

import java.io.IOException

import org.http4s.Header

sealed trait Rejection

final case class AuthenticationFailedRejection(reason: RejectionReason, challenge: Header) extends Rejection

sealed trait RejectionReason

case object CredentialsMissing extends RejectionReason

case object CredentialsRejected extends RejectionReason

final case class MalformedHeaderRejection(headerName: String, errorMsg: String, cause: Option[Throwable])
    extends Rejection

final case class ServerErrorRejection(error: IOException) extends Rejection