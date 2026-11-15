package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.network.CALL_TIMEOUT_SECONDS
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The client every call that carries the player's LinkedIn session goes through (ADR-0028 §2-3, ADR-0032).
 * The session is a login, so this client does nothing on its own account. No cookie jar: a rotated cookie
 * in a response is dropped, never re-sent. No interceptor, so nothing can log a header or a body. A
 * redirect is an answer, not a hop to follow. A failed connection is not retried, so one call is one request.
 */
internal fun linkedInSessionClient(): OkHttpClient =
    OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
