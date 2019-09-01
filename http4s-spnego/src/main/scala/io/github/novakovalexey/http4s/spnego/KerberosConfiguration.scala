package io.github.novakovalexey.http4s.spnego

import javax.security.auth.login.{AppConfigurationEntry, Configuration}
// scala 2.12 needs to be supported
import collection.JavaConverters._

case class KerberosConfiguration(keytab: String, principal: String, debug: Boolean, ticketCache: Option[String]) extends Configuration {

  override def getAppConfigurationEntry(name: String): Array[AppConfigurationEntry] = Array(
    new AppConfigurationEntry(
      "com.sun.security.auth.module.Krb5LoginModule",
      AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
      (Map(
        "keyTab" -> keytab,
        "principal" -> principal,
        "useKeyTab" -> "true",
        "storeKey" -> "true",
        "doNotPrompt" -> "true",
        "useTicketCache" -> "true",
        "renewTGT" -> "true",
        "isInitiator" -> "false",
        "refreshKrb5Config" -> "true",
        "debug" -> debug.toString
      ) ++ ticketCache.map { x =>
        Map("ticketCache" -> x)
      }.getOrElse(Map.empty)).asJava
    )
  )
}
