package io.github.novakovalexey.http4s.spnego

import java.util.Base64

object Base64Util {
  def encode(b: Array[Byte]): String = Base64.getEncoder.encodeToString(b)
  def decode(s: String): Array[Byte] = Base64.getDecoder.decode(s)
}
