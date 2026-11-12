package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.network.CALL_TIMEOUT_SECONDS
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * The production reader of the player's own LinkedIn account over [baseUrl], with a client of its own.
 * Tests build it through here too, so they run the wire the app runs.
 */
fun linkedInAccountSource(baseUrl: String, session: LinkedInSessionCookies): LinkedInAccountSource {
    // No converter: the body is read raw, so the shared JSON-converting Retrofit is not needed.
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(linkedInAccountClient())
        .build()
        .create(LinkedInAccountApi::class.java)
    return VoyagerLinkedInAccountSource(api, session)
}

/**
 * The session is a login, so this client does nothing on its own account. No cookie jar: a rotated cookie
 * in a response is dropped, never re-sent. No interceptor, so nothing can log a header or a body. A
 * redirect is an answer, not a hop to follow. A failed connection is not retried, so one call is one GET.
 */
private fun linkedInAccountClient(): OkHttpClient =
    OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
