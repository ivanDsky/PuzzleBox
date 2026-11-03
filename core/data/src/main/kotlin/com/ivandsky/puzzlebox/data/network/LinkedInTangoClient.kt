package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Fetches today's Tango body through the rotation-tolerant ladder (research R-19-1) —
 * the [LinkedInZipClient] shape with the Tango route:
 *
 * 1. GET the streaming screen-load (`games/tango/?skipStartScreen=true` with the
 *    `x-li-rsc-stream` + `x-li-initial-url` pair) — stateless, cookieless, csrf-free —
 *    and scan the React Flight stream.
 * 2. A 200 with a zero-byte body and a `location` header is LinkedIn's per-IP throttle
 *    (~5 fetches inside a minute, ~100 s cooldown). Retryable; resurfaces as
 *    [FetchError.Throttled] — never as "no data".
 * 3. Scan the plain page shell ([LinkedInTangoApi.page]) as the last network resort.
 * 4. A bare 403 (no throttle signature) is [FetchError.Unavailable], not Throttled.
 *
 * Any body carrying the tangoGamePuzzle object wins — the extractor, not the URL,
 * decides. The caller's backstop is degrade-to-cache (A7/A8).
 */
open class LinkedInTangoClient(private val api: LinkedInTangoApi) {

    /** Raw body text that [TangoGameExtractor] can extract from, or a typed failure. */
    open suspend fun fetchDailyBody(): FetchResult<String> {
        val reasons = mutableListOf<String>()
        var throttled = false
        try {
            val response = api.daily()
            if (response.isSuccessful) {
                val text = response.body()?.string().orEmpty()
                if (TangoGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                if (text.isEmpty() && response.headers()["location"] != null) {
                    throttled = true
                    reasons += "throttle: empty 200 with location ${response.headers()["location"]}"
                } else {
                    reasons += "stream body carried no tangoGamePuzzle (${text.length}B)"
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
                if (TangoGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                reasons += "page body carried no tangoGamePuzzle (${text.length}B)"
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
