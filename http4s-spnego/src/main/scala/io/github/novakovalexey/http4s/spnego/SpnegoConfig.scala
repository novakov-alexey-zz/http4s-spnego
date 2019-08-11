package io.github.novakovalexey.http4s.spnego

import cats.Show

import scala.concurrent.duration.Duration

case class SpnegoConfig(
  kerberosPrincipal: String,
  kerberosRealm: String,
  kerberosKeytab: String,
  kerberosDebug: Boolean,
  kerberosTicketCache: Option[String],
  signatureSecret: String,
  domain: Option[String],
  path: Option[String],
  cookieName: String,
  tokenValidity: Duration
)

object SpnegoConfig {
  implicit val cfg: Show[SpnegoConfig] = (c: SpnegoConfig) => s"""http4s.spnego config:
                                                                 |principal: ${c.kerberosPrincipal}
                                                                 |realm: ${c.kerberosRealm}
                                                                 |keytab: ${c.kerberosKeytab}
                                                                 |debug: ${c.kerberosDebug}
                                                                 |ticketCache: ${c.kerberosTicketCache}
                                                                 |domain: ${c.domain}
                                                                 |path: ${c.path}
                                                                 |cookieName: ${c.cookieName}
                                                                 |tokenValidity: ${c.tokenValidity}
                                                                 |""".stripMargin
}
