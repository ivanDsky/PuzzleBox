package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Fetches public profile photos with a client that knows nothing of the player's login. The url check is the
 * last line of defence, not the first: whatever builds a url, nothing but an https url on [HOST] is ever
 * requested, so a changed response cannot point the app at another server. Reads at most [MAX_BYTES] of a body.
 */
internal class OkHttpLinkedInPhotoSource(private val client: OkHttpClient) : LinkedInPhotoSource {

    override suspend fun fetch(url: String): ByteArray? {
        val target = url.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == HOST } ?: return null
        return try {
            client.newCall(Request.Builder().url(target).get().build()).await().use(::imageBytes)
        } catch (_: IOException) {
            // OkHttp reports a cancelled call as an IOException; that is cancellation, not a dead network.
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private fun imageBytes(response: Response): ByteArray? {
        val body = response.body
        // A redirect is an answer too: following it could leave the image host.
        if (!response.isSuccessful || body.contentType()?.type != IMAGE) return null
        if (body.contentLength() > MAX_BYTES) return null
        val source = body.source()
        // Asking for one byte past the cap buffers it only when the body is over: the cap is never crossed unseen.
        source.request(MAX_BYTES + 1)
        if (source.buffer.size > MAX_BYTES) return null
        return source.readByteArray()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            },
        )
    }

    private companion object {
        const val HOST = "media.licdn.com"
        const val IMAGE = "image"
        const val MAX_BYTES = 512L * 1024
    }
}
