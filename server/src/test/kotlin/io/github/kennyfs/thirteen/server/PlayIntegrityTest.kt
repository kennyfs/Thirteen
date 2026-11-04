package io.github.kennyfs.thirteen.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64

class PlayIntegrityTest {
  private fun verdict(nonce: String = "abc", pkg: String = "io.github.kennyfs.thirteen", app: String = "PLAY_RECOGNIZED", device: String = "MEETS_DEVICE_INTEGRITY", at: Long = 1_000_000L) =
    Json.parseToJsonElement(
      """{"tokenPayloadExternal":{"requestDetails":{"requestPackageName":"$pkg","nonce":"$nonce","timestampMillis":"$at"},
      "appIntegrity":{"appRecognitionVerdict":"$app","packageName":"$pkg"},"deviceIntegrity":{"deviceRecognitionVerdict":["$device"]}}}""",
    ).jsonObject

  @Test
  fun onlyTheAppFromGooglePlayOnAGenuineDeviceForThisNoncePasses() {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n"
    val file = File.createTempFile("key", ".json").apply {
      writeText(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
        put("client_email", kotlinx.serialization.json.JsonPrimitive("x@y.iam.gserviceaccount.com"))
        put("private_key", kotlinx.serialization.json.JsonPrimitive(pem))
      }))
      deleteOnExit()
    }
    val p = PlayIntegrity(file.path, "io.github.kennyfs.thirteen") { 1_060_000L }
    assertTrue(p.passes(verdict(), "abc"))
    assertFalse(p.passes(verdict(), "abd"))
    assertFalse(p.passes(verdict(pkg = "io.github.kennyfs.thirteen.test"), "abc"))
    assertFalse(p.passes(verdict(app = "UNRECOGNIZED_VERSION"), "abc"))
    assertFalse(p.passes(verdict(device = "MEETS_BASIC_INTEGRITY"), "abc"))
    assertFalse(p.passes(verdict(at = 0L), "abc"))
  }
}
