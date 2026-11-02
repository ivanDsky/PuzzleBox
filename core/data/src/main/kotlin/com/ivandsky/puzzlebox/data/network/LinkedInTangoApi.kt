package com.ivandsky.puzzlebox.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * LinkedIn's unofficial Tango screen endpoints (research R-19-1, anonymous; A7 — no sign-in).
 * Same ladder shape as [LinkedInZipApi]: the streaming screen-load GET gated by the
 * `x-li-rsc-stream` + `x-li-initial-url` pair is the primary route — stateless,
 * cookieless, csrf-free — and the response is a React Flight stream carrying a
 * plain-quoted `tangoGamePuzzle` (inferred; ticket 06 validates live). [daily] returns a
 * [Response] wrapper so the throttle signature (empty 200 + `location` header) is
 * observable. Parsing is [TangoGameExtractor]'s job — never a pinned path.
 */
interface LinkedInTangoApi {

    /** Primary: the streaming screen-load GET; response is the React Flight stream. */
    @Headers(
        "x-li-rsc-stream: true",
        "x-li-initial-url: /games/tango/",
    )
    @GET("games/tango/")
    suspend fun daily(
        @Query("skipStartScreen") skipStartScreen: Boolean = true,
    ): Response<ResponseBody>

    /** Opportunistic last scan target: the SPA shell (anonymous SSR carries no puzzle today; R-19-1). */
    @GET("games/tango/")
    suspend fun page(): ResponseBody
}
