package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Fetches today's Zip body through the rotation-tolerant ladder (research R-19-1):
 *
 * 1. GET the streaming screen-load (`games/zip/?skipStartScreen=true` with the
 *    `x-li-rsc-stream` + `x-li-initial-url` pair) — stateless, cookieless, csrf-free —
 *    and scan the React Flight stream.
 * 2. A 200 with a zero-byte body and a `location` header is LinkedIn's per-IP throttle
 *    (~5 fetches inside a minute, ~100 s cooldown). It is remembered as retryable and
 *    resurfaces as [FetchError.Throttled] — never as "no data". No silent looping: the
 *    ladder keeps walking once, then reports.
 * 3. Scan the plain page shell ([LinkedInZipApi.page]) as the last network resort —
 *    dead today, but LinkedIn may inline SSR data again at any build.
 *
 * Any body carrying the trailGamePuzzle object wins — the extractor, not the URL,
 * decides. The cookie jar / csrf interceptor stay on the OkHttp client (provably
 * irrelevant on this route) but nothing here depends on them. The caller's backstop
 * is degrade-to-cache (A7/A8).
 */
class LinkedInZipClient(private val api: LinkedInZipApi) {

    /** Raw body text that [TrailGameExtractor] can extract from, or a typed failure. */
    suspend fun fetchDailyBody(): FetchResult<String> {
        val reasons = mutableListOf<String>()
        var throttled = false
        try {
            val response = api.daily()
            if (response.isSuccessful) {
                val text = response.body()?.string().orEmpty()
                if (TrailGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                if (text.isEmpty() && response.headers()["location"] != null) {
                    throttled = true
                    reasons += "throttle: empty 200 with location ${response.headers()["location"]}"
                } else {
                    reasons += "stream body carried no trailGamePuzzle (${text.length}B)"
                }
            } else {
                reasons += "stream HTTP ${response.code()}"
                response.errorBody()?.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reasons += "stream transport: ${e.javaClass.simpleName}"
        } catch (e: HttpException) {
            reasons += "stream HTTP ${e.code()}"
        }
        try {
            api.page().use { pageBody ->
                val text = pageBody.string()
                if (TrailGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                reasons += "page body carried no trailGamePuzzle (${text.length}B)"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reasons += "page transport: ${e.javaClass.simpleName}"
        } catch (e: HttpException) {
            reasons += "page HTTP ${e.code()}"
        }
        val detail = reasons.joinToString("; ")
        return if (throttled) {
            FetchResult.Err(FetchError.Throttled(detail))
        } else {
            FetchResult.Err(FetchError.Unavailable(detail))
        }
    }
}
