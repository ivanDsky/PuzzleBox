package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

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
 * 3. If the request carries a [FeedRequest.graphqlBody], POST it through the session
 *    seams (cookie jar + csrf-token echo on the OkHttp client) and scan the response.
 * 4. If [FeedRequest.scanPageShell], scan the plain page shell as the last network
 *    resort — dead today, but LinkedIn may inline SSR data again at any build.
 *
 * Any body the request [FeedRequest.accepts] wins — the game's extractor, not the URL,
 * decides. The caller's backstop is degrade-to-cache (A7/A8).
 */
class LinkedInFeedTransport(private val api: LinkedInGamesApi) : FeedTransport {

    override suspend fun fetch(request: FeedRequest): FetchResult<String> {
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
        request.graphqlBody?.let { query ->
            rung("graphql", reasons) {
                val response = api.graphql(query.toRequestBody(JSON_MEDIA_TYPE))
                if (response.isSuccessful) {
                    val text = response.body()?.string().orEmpty()
                    if (request.accepts(text)) return FetchResult.Ok(text)
                    reasons += "graphql body carried no ${request.objectName} (${text.length}B)"
                    if (text.isEmpty() && response.headers()["location"] != null) throttled = true
                } else {
                    reasons += "graphql HTTP ${response.code()}"
                    response.errorBody()?.close()
                }
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
            FetchResult.Err(FetchError.Throttled(detail))
        } else {
            FetchResult.Err(FetchError.Unavailable(detail))
        }
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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
