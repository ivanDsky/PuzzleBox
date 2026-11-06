package com.ivandsky.puzzlebox.network

import com.ivandsky.puzzlebox.game.FeedTransport
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
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

/** LinkedIn's observed per-IP throttle window (research R-19-1: ~100 s); fetches inside it are refused locally (A17). */
val LINKEDIN_THROTTLE_COOLDOWN: Duration = Duration.ofSeconds(100)

/**
 * The production LinkedIn transport (A5, A7, A17): a fresh per-process session jar, the
 * signed [linkedInClient] over it, and the rung ladder over [baseUrl]. `networkModule`
 * binds exactly this, so a test that builds it runs the wire the app runs.
 */
fun linkedInFeedTransport(baseUrl: String, json: Json, clock: Clock = Clock.systemUTC()): FeedTransport {
    val jar = LinkedInSessionCookieJar()
    val api = retrofit(baseUrl = baseUrl, client = linkedInClient(jar), json = json)
        .create(LinkedInGamesApi::class.java)
    return LinkedInFeedTransport(api, jar, baseUrl.toHttpUrl(), clock)
}

/**
 * Client for the anonymous LinkedIn screen endpoints (research 01 §2): cookie jar for the
 * JSESSIONID handshake, csrf interceptor echoing it, logging that redacts both session
 * vectors, and the same whole-call timeout as NYT's client.
 */
internal fun linkedInClient(jar: LinkedInSessionCookieJar): OkHttpClient =
    OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

/** Whole-call budget shared by both clients (A18): a stalled fetch degrades to cache, never hangs. */
private const val CALL_TIMEOUT_SECONDS = 10L
