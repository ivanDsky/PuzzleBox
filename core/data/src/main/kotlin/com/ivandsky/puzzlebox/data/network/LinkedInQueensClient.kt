package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.queens.feed.QueensGameExtractor
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Fetches today's Queens body through the rotation-tolerant ladder (research 02, zip
 * LinkedInZipClient precedent):
 *
 * 1. GET the streaming screen-load (`games/queens/?skipStartScreen=true`, stateless,
 *    cookieless, csrf-free) and scan the React Flight stream.
 * 2. POST the GraphQL query (`gameTypeId:3`) through the session seams — cookie jar +
 *    csrf-token echo — and scan the `included[].gamePuzzle.queensGamePuzzle` response.
 * 3. A 200 with a zero-byte body and a `location` header is LinkedIn's per-IP throttle
 *    (zip R-19-1 signature). Remembered as retryable, resurfaces as
 *    [FetchError.Throttled] — never as "no data".
 *
 * Any body carrying the queensGamePuzzle object wins — the extractor, not the URL,
 * decides. The caller's backstop is degrade-to-cache (A7/A8).
 */
class LinkedInQueensClient(private val api: LinkedInQueensApi) {

    /** Raw body text that [QueensGameExtractor] can extract from, or a typed failure. */
    suspend fun fetchDailyBody(): FetchResult<String> {
        val reasons = mutableListOf<String>()
        var throttled = false
        try {
            val response = api.daily()
            if (response.isSuccessful) {
                val text = response.body()?.string().orEmpty()
                if (QueensGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                if (text.isEmpty() && response.headers()["location"] != null) {
                    throttled = true
                    reasons += "throttle: empty 200 with location ${response.headers()["location"]}"
                } else {
                    reasons += "stream body carried no queensGamePuzzle (${text.length}B)"
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
            val response = api.graphql(GRAPHQL_QUERY.toRequestBody(JSON_MEDIA_TYPE))
            if (response.isSuccessful) {
                val text = response.body()?.string().orEmpty()
                if (QueensGameExtractor.extract(text) != null) return FetchResult.Ok(text)
                reasons += "graphql body carried no queensGamePuzzle (${text.length}B)"
                if (text.isEmpty() && response.headers()["location"] != null) throttled = true
            } else {
                reasons += "graphql HTTP ${response.code()}"
                response.errorBody()?.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            reasons += "graphql transport: ${e.javaClass.simpleName}"
        } catch (e: HttpException) {
            reasons += "graphql HTTP ${e.code()}"
        }
        val detail = reasons.joinToString("; ")
        return if (throttled) {
            FetchResult.Err(FetchError.Throttled(detail))
        } else {
            FetchResult.Err(FetchError.Unavailable(detail))
        }
    }

    private companion object {
        // gameTypeId 3 = Queens (research 02; linkedin-games-scraper GAME_TYPE_IDS).
        const val GAME_TYPE_ID = 3
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Documented minimal GraphQL request (research 02). The exact production queryId
        // document is queens-09's real-capture job; the extractor accepts ANY body that
        // carries the queensGamePuzzle object.
        val GRAPHQL_QUERY =
            """{"query":"query QueensDailyGame { voyagerIdentityDashGames(gameTypeId: $GAME_TYPE_ID) { included { gamePuzzle { queensGamePuzzle { gridSize colorGrid { colors } solution { row col } } } } } }","variables":{"gameTypeId":$GAME_TYPE_ID}}"""
    }
}
