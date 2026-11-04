package io.github.kennyfs.thirteen.server

import io.github.kennyfs.thirteen.protocol.Attestor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64

/**
 * Play Integrity, checked with Google: a client's token is decoded by Google's API (playintegrity.googleapis.com,
 * as the service account of `keyFile`, a Google Cloud service account key in JSON with access to the app's Play
 * Integrity), and passes when it is for this nonce and app, recent, the app is the one Google Play has
 * (PLAY_RECOGNIZED), and the device passes device integrity. The server needs to reach Google for this.
 */
class PlayIntegrity(keyFile: String, private val packageName: String, private val now: () -> Long = System::currentTimeMillis) : Attestor {
  private val json = Json { ignoreUnknownKeys = true }
  private val key = json.parseToJsonElement(File(keyFile).readText()).jsonObject
  private val email = key.getValue("client_email").jsonPrimitive.content
  private val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
    PKCS8EncodedKeySpec(
      Base64.getMimeDecoder().decode(
        key.getValue("private_key").jsonPrimitive.content.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""),
      ),
    ),
  )
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
  private val mutex = Mutex()
  private var access: String? = null
  private var accessUntil = 0L

  override suspend fun verify(token: String, nonce: String): Boolean {
    val body = buildJsonObject { put("integrity_token", token) }.toString()
    val answer = post(
      "https://playintegrity.googleapis.com/v1/$packageName:decodeIntegrityToken", body, "application/json", accessToken(),
    ) ?: return false
    return passes(answer, nonce)
  }

  /** Whether a decoded verdict passes for this nonce. */
  fun passes(answer: JsonObject, nonce: String): Boolean {
    val payload = answer["tokenPayloadExternal"]?.jsonObject ?: return false
    val request = payload["requestDetails"]?.jsonObject ?: return false
    if (request["requestPackageName"]?.jsonPrimitive?.content != packageName) return false
    if (request["nonce"]?.jsonPrimitive?.content?.trimEnd('=') != nonce.trimEnd('=')) return false
    val at = request["timestampMillis"]?.jsonPrimitive?.content?.toLongOrNull() ?: return false
    if (kotlin.math.abs(now() - at) > MAX_AGE_MS) return false
    val app = payload["appIntegrity"]?.jsonObject ?: return false
    if (app["appRecognitionVerdict"]?.jsonPrimitive?.content != "PLAY_RECOGNIZED") return false
    val device = payload["deviceIntegrity"]?.jsonObject?.get("deviceRecognitionVerdict")?.jsonArray?.map { it.jsonPrimitive.content } ?: return false
    return "MEETS_DEVICE_INTEGRITY" in device
  }

  /** An OAuth access token for the service account, kept until shortly before it expires. */
  private suspend fun accessToken(): String? = mutex.withLock {
    access?.takeIf { now() < accessUntil }?.let { return it }
    val t = now() / 1000
    fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    val head = b64("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
    val claims = b64(
      buildJsonObject {
        put("iss", email)
        put("scope", "https://www.googleapis.com/auth/playintegrity")
        put("aud", TOKEN_URL)
        put("iat", t)
        put("exp", t + 3600)
      }.toString().toByteArray(),
    )
    val signature = Signature.getInstance("SHA256withRSA").apply {
      initSign(privateKey)
      update("$head.$claims".toByteArray())
    }.sign()
    val form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=$head.$claims.${b64(signature)}"
    val answer = post(TOKEN_URL, form, "application/x-www-form-urlencoded", null) ?: return null
    val token = answer["access_token"]?.jsonPrimitive?.content ?: return null
    val life = answer["expires_in"]?.jsonPrimitive?.long ?: 3600
    access = token
    accessUntil = now() + (life - 300) * 1000
    token
  }

  private suspend fun post(url: String, body: String, type: String, bearer: String?): JsonObject? = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10)).header("Content-Type", type)
      .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
      .POST(HttpRequest.BodyPublishers.ofString(body)).build()
    val response = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }.getOrNull() ?: return@withContext null
    if (response.statusCode() != 200) return@withContext null
    runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
  }

  companion object {
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    /** How old a verdict may be. */
    const val MAX_AGE_MS = 5 * 60_000L
  }
}
