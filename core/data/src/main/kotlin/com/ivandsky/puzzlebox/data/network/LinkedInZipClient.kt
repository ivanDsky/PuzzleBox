package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Fetches today's Zip body through the rotation-tolerant ladder (research 01 §2/§4):
 *
 * 1. GET the games page — the anonymous handshake: LinkedIn mints the `JSESSIONID`
 *    cookie whose value the game POST must echo as `csrf-token`. The page body is kept
 *    for the fallback scan (on the current shell it carries no puzzle data — 2026-10-31
 *    capture — but LinkedIn may inline SSR data again at any build).
 * 2. POST the flagship screen endpoint (RSC flight stream) and scan it.
 * 3. If the stream carried nothing data-bearing, scan the page body from step 1.
 *
 * Any body carrying the trailGamePuzzle object wins — the extractor, not the URL,
 * decides. The caller's backstop is degrade-to-cache (A7/A8).
 */
class LinkedInZipClient(private val api: LinkedInZipApi) {

    /** Raw body text that [TrailGameExtractor] can extract from, or a typed failure. */
    suspend fun fetchDailyBody(): FetchResult<String> {
        val reasons = mutableListOf<String>()
        val pageBody = try {
            api.page().use { it.string() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reasons += "handshake transport: ${e.javaClass.simpleName}"
            null
        } catch (e: HttpException) {
            reasons += "handshake HTTP ${e.code()}"
            null
        }
        try {
            api.daily().use { response ->
                val text = response.string()
                if (TrailGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                reasons += "stream body carried no trailGamePuzzle (${text.length}B)"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reasons += "stream transport: ${e.javaClass.simpleName}"
        } catch (e: HttpException) {
            reasons += "stream HTTP ${e.code()}"
        }
        if (pageBody != null) {
            if (TrailGameExtractor.extract(pageBody) != null) return FetchResult.Ok(pageBody)
            reasons += "page body carried no trailGamePuzzle (${pageBody.length}B)"
        }
        return FetchResult.Err(FetchError.Unavailable(reasons.joinToString("; ")))
    }
}
