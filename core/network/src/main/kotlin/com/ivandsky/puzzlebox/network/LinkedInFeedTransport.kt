package com.ivandsky.puzzlebox.network

import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Fetches a game's daily body through the rotation-tolerant ladder (research R-19-1 / 02),
 * the one LinkedIn transport every game's DailySource shares:
 *
 * 1. GET the streaming screen-load (`<path>?skipStartScreen=true` with the
 *    `x-li-rsc-stream` + `x-li-initial-url` pair) — stateless, cookieless, csrf-free —
 *    and scan the React Flight stream.
 * 2. A 200 with a zero-byte body and a `location` header is LinkedIn's per-IP throttle
 *    (~5 fetches inside a minute, ~100 s cooldown). It is remembered as retryable and
 *    resurfaces as [FetchError.Throttled] — never as "no data". No silent looping: the
 *    ladder keeps walking once, then reports.
 * 3. If the request names a [FeedRequest.persistedQuery], GET
 *    `voyager/api/graphql?variables=<v>&queryId=<id>` with both params verbatim (ADR-0016).
 *    It runs after the stream because the stream's `JSESSIONID` cookie is what the
 *    csrf-token echo signs it with.
 * 4. If the request carries a [FeedRequest.graphqlBody], POST it through the session
 *    seams (cookie jar + csrf-token echo on the OkHttp client) and scan the response.
 * 5. If [FeedRequest.scanPageShell], scan the plain page shell as the last network
 *    resort — dead today, but LinkedIn may inline SSR data again at any build.
 *
 * Any body the request [FeedRequest.accepts] wins — the game's extractor, not the URL,
 * decides. The caller's backstop is degrade-to-cache (A7/A8).
 *
 * After a throttle, every fetch within [THROTTLE_COOLDOWN] of it returns
 * [FetchError.Throttled] without a request (A17).
 */
internal class LinkedInFeedTransport(
    private val api: LinkedInGamesApi,
    private val clock: Clock = Clock.systemUTC(),
) : FeedTransport {

    /** Shared across games: the throttle is per-IP, so one game's throttle blocks them all. */
    @Volatile
    private var cooldownUntil: Instant = Instant.MIN

    override suspend fun fetch(request: FeedRequest): FetchResult<String> {
        // WHY: every request inside the window extends LinkedIn's throttle; fail fast instead.
        if (clock.instant().isBefore(cooldownUntil)) {
            return FetchResult.Err(FetchError.Throttled("cooldown until $cooldownUntil"))
        }
        val reasons = mutableListOf<String>()
        var throttled = false
        rung("stream", reasons) {
            val response = api.stream(request.path, initialUrl = "/" + request.path)
            if (response.isSuccessful) {
                val text = response.body()?.string().orEmpty()
                if (request.accepts(text)) return FetchResult.Ok(text)
                if (text.isEmpty() && response.headers()["location"] != null) {
                    throttled = true
                    reasons += "throttle: empty 200 with location ${response.headers()["location"]}"
                } else {
                    reasons += "stream body carried no ${request.objectName} (${text.length}B)"
                }
            } else {
                reasons += "stream HTTP ${response.code()}"
                response.errorBody()?.close()
            }
        }
        request.persistedQuery?.let { query ->
            rung(PERSISTED_QUERY, reasons) {
                val response = api.persistedQuery(variables = query.variables, queryId = query.queryId)
                scanSessionRung(PERSISTED_QUERY, response, request, reasons) { throttled = true }
                    ?.let { return FetchResult.Ok(it) }
            }
        }
        request.graphqlBody?.let { query ->
            rung(GRAPHQL, reasons) {
                val response = api.graphql(query.toRequestBody(JSON_MEDIA_TYPE))
                scanSessionRung(GRAPHQL, response, request, reasons) { throttled = true }
                    ?.let { return FetchResult.Ok(it) }
            }
        }
        if (request.scanPageShell) {
            rung("page", reasons) {
                api.page(request.path).use { pageBody ->
                    val text = pageBody.string()
                    if (request.accepts(text)) return FetchResult.Ok(text)
                    reasons += "page body carried no ${request.objectName} (${text.length}B)"
                }
            }
        }
        val detail = reasons.joinToString("; ")
        return if (throttled) {
            cooldownUntil = clock.instant().plus(THROTTLE_COOLDOWN)
            FetchResult.Err(FetchError.Throttled(detail))
        } else {
            FetchResult.Err(FetchError.Unavailable(detail))
        }
    }

    /**
     * Scans a cookie-jar + csrf-signed rung's response: the accepted body, or null after
     * recording why it was refused (and flagging [onThrottle] on the throttle signature).
     */
    private inline fun scanSessionRung(
        name: String,
        response: Response<ResponseBody>,
        request: FeedRequest,
        reasons: MutableList<String>,
        onThrottle: () -> Unit,
    ): String? {
        if (!response.isSuccessful) {
            reasons += "$name HTTP ${response.code()}"
            response.errorBody()?.close()
            return null
        }
        val text = response.body()?.string().orEmpty()
        if (request.accepts(text)) return text
        reasons += "$name body carried no ${request.objectName} (${text.length}B)"
        if (text.isEmpty() && response.headers()["location"] != null) onThrottle()
        return null
    }

    /** Runs one ladder rung, recording its transport/HTTP failure and moving on. */
    private inline fun rung(name: String, reasons: MutableList<String>, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reasons += "$name transport: ${e.javaClass.simpleName}"
        } catch (e: HttpException) {
            reasons += "$name HTTP ${e.code()}"
        }
    }

    internal companion object {
        /** LinkedIn's observed per-IP throttle window (research R-19-1: ~100 s). */
        val THROTTLE_COOLDOWN: Duration = Duration.ofSeconds(100)
        private const val PERSISTED_QUERY = "persisted-query"
        private const val GRAPHQL = "graphql"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
