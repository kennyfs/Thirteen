package io.github.kennyfs.thirteen.battle

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import io.github.kennyfs.thirteen.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Play Integrity for a server that asks (Welcome's nonce): a token Google signs, which the server has Google decode
 * to know the client is this app as Google Play installed it, on a genuine device. Only the build for Google Play
 * (THIRTEEN_PLAY_PROJECT set) asks for one; null when it cannot, and the client goes on unchecked.
 */
object Integrity {
  val enabled: Boolean get() = BuildConfig.PLAY_PROJECT != 0L

  suspend fun token(context: Context, nonce: String): String? {
    if (!enabled) return null
    return suspendCancellableCoroutine { cont ->
      IntegrityManagerFactory.create(context.applicationContext)
        .requestIntegrityToken(IntegrityTokenRequest.builder().setNonce(nonce).setCloudProjectNumber(BuildConfig.PLAY_PROJECT).build())
        .addOnSuccessListener { if (cont.isActive) cont.resume(it.token()) }
        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }
  }
}
