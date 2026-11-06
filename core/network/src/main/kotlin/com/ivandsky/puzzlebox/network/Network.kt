package com.ivandsky.puzzlebox.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Placeholder base URL; Retrofit requires one at build time. Phase 1 finalizes the real hosts. */
internal const val LINKEDIN_BASE_URL = "https://www.linkedin.com/"

/** NYT's public game JSON host (ADR-0014); paths are relative to it. */
internal const val NYT_BASE_URL = "https://www.nytimes.com/"

internal fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

/**
 * Client for the anonymous LinkedIn screen endpoints (research 01 §2): cookie jar for the
 * JSESSIONID handshake, csrf interceptor echoing it, logging that redacts both session
 * vectors.
 */
internal fun linkedInClient(jar: LinkedInSessionCookieJar): OkHttpClient =
    OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(LinkedInCsrfInterceptor(jar))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                // WHY: the csrf token is the session cookie's value — never log either.
                redactHeader("csrf-token")
                redactHeader("Cookie")
            },
        )
        .build()

/**
 * Client for NYT's anonymous JSON endpoints: no session, no cookies, and a whole-call
 * timeout so a stalled fetch surfaces as Unavailable instead of hanging the daily load.
 */
internal fun nytClient(): OkHttpClient =
    OkHttpClient.Builder()
        .callTimeout(NYT_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

private const val NYT_CALL_TIMEOUT_SECONDS = 10L
