package com.ivandsky.puzzlebox.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * LinkedIn's unofficial Zip screen endpoints (research R-19-1, anonymous; A7 — no sign-in).
 * The primary route is the screen-load GET LinkedIn's own web client sends: stateless,
 * cookieless, csrf-free — gated only by the `x-li-rsc-stream` + `x-li-initial-url` header
 * pair. The response is a React Flight stream (`application/octet-stream`) carrying a
 * plain-quoted `trailGamePuzzle`. [daily] returns a [Response] wrapper so the throttle
 * signature (empty 200 + `location` header) is observable. Parsing is [TrailGameExtractor]'s
 * job — never a pinned path. (The old SDUI NavigateToScreen POST died under CSRF
 * enforcement 2026-11-01 and is gone for good.)
 */
interface LinkedInZipApi {

    /** Primary: the streaming screen-load GET; response is the React Flight stream. */
    @Headers(
        "x-li-rsc-stream: true",
        "x-li-initial-url: /games/zip/",
    )
    @GET("games/zip/")
    suspend fun daily(
        @Query("skipStartScreen") skipStartScreen: Boolean = true,
    ): Response<ResponseBody>

    /** Opportunistic last scan target: the SPA shell (anonymous SSR carries no puzzle today; R-19-1). */
    @GET("games/zip/")
    suspend fun page(): ResponseBody
}
