package io.github.novakovalexey.http4s.spnego

import java.util

import javax.security.auth.login.{AppConfigurationEntry, Configuration}

case class KerberosConfiguration(keytab: String, principal: String, debug: Boolean, ticketCache: Option[String])
    extends Configuration {

  val cfg = new util.HashMap[String, String]()
  cfg.put("keyTab", keytab)
  cfg.put("principal", principal)
  cfg.put("useKeyTab", "true")
  cfg.put("storeKey", "true")
  cfg.put("doNotPrompt", "true")
  cfg.put("useTicketCache", "true")
  cfg.put("renewTGT", "true")
  cfg.put("isInitiator", "false")
  cfg.put("refreshKrb5Config", "true")
  cfg.put("debug", debug.toString)
  ticketCache.foreach(tc => cfg.put("ticketCache", tc))

  override def getAppConfigurationEntry(name: String): Array[AppConfigurationEntry] = Array(
    new AppConfigurationEntry(
      "com.sun.security.auth.module.Krb5LoginModule",
      AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
      cfg
    )
  )
}
