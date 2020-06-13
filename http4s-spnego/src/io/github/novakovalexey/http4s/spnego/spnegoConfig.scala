package io.github.novakovalexey.http4s.spnego

import cats.Show

import scala.concurrent.duration.Duration

final case class SpnegoConfig(
  kerberosRealm: String,
  principal: String,
  signatureSecret: String,
  domain: Option[String],
  path: Option[String],
  tokenValidity: Duration,
  cookieName: String = "http4s.spnego",
  jaasConfig: Option[JaasConfig] = None
)

final case class JaasConfig(keytab: String, debug: Boolean, ticketCache: Option[String], isInitiator: Boolean = false)

object SpnegoConfig {
  val JaasConfigEntryName = "Server"

  implicit val cfg: Show[SpnegoConfig] = (c: SpnegoConfig) => s"""http4s-spnego config:
                                                                 |realm: ${c.kerberosRealm}
                                                                 |principal: ${c.principal}
                                                                 |jaasConfig: ${c.jaasConfig}
                                                                 |domain: ${c.domain}
                                                                 |path: ${c.path}
                                                                 |tokenValidity: ${c.tokenValidity}
                                                                 |cookieName: ${c.cookieName}
                                                                 |""".stripMargin
}
