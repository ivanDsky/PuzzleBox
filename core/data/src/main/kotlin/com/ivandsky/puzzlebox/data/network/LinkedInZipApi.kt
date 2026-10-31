package com.ivandsky.puzzlebox.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * LinkedIn's unofficial Zip screen endpoints (research 01, anonymous; A7 — no sign-in).
 * Both return opaque bodies: the puzzle rides an RSC flight stream (`application/octet-stream`)
 * or, as fallback, is scanned out of the page HTML. Parsing is [TrailGameExtractor]'s job —
 * never a pinned path.
 */
interface LinkedInZipApi {

    /** Primary: the flagship-web SDUI screen POST; response is the RSC flight stream. */
    @Headers(
        "x-li-rsc-stream: true",
        "referer: https://www.linkedin.com/games/zip/?skipStartScreen=true",
    )
    @POST("flagship-web/games/zip/")
    suspend fun daily(
        @Query("skipStartScreen") skipStartScreen: Boolean = true,
        @Body action: RequestBody = SDUI_ZIP_NAVIGATE_BODY,
    ): ResponseBody

    /** Fallback scan target: the SPA shell (SSR data if LinkedIn ever inlines it). */
    @GET("games/zip/")
    suspend fun page(): ResponseBody

    companion object {
        /**
         * The SDUI NavigateToScreen action the games page itself sends (research 01,
         * captured verbatim 2026-10-31). A constant, not a model — the body is LinkedIn's
         * screen-routing protocol, not our domain.
         */
        val SDUI_ZIP_NAVIGATE_BODY: RequestBody =
            (
                "{\"\$type\":\"proto.sdui.actions.core.NavigateToScreen\"," +
                    "\"screenId\":\"com.linkedin.sdui.flagshipnav.games.GamePlayRouterPage\"," +
                    "\"url\":\"/games/zip/?skipStartScreen=true\"," +
                    "\"title\":\"Zip\"," +
                    "\"requestedArguments\":{\"payload\":{\"gameType\":\"zip\",\"skipStartScreen\":true},\"states\":[]}}"
                ).toRequestBody("application/json; charset=utf-8".toMediaType())
    }
}
