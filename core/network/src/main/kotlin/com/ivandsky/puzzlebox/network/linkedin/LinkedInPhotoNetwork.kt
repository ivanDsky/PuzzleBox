package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.network.CALL_TIMEOUT_SECONDS
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * The production photo downloader. [answer] stands in for the network in tests, so they can see every
 * request the client would send; the app passes none.
 *
 * The client is built from nothing, never from the account client, so no session can reach it: it
 * keeps no cookies between calls, adds no header of its own, and logs nothing. A redirect is an answer,
 * not a hop to follow, and a failed connection is not retried, so one photo is one GET.
 */
fun linkedInPhotoSource(answer: Interceptor? = null): LinkedInPhotoSource {
    val client = OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
    answer?.let(client::addInterceptor)
    return OkHttpLinkedInPhotoSource(client.build())
}
