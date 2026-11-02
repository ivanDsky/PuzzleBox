package com.ivandsky.puzzlebox.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * LinkedIn's unofficial Queens endpoints (research 02-endpoint-wire-format.md, anonymous;
 * A7 — no sign-in). The Queens game type id is 3 (`GAME_TYPE_IDS["queens"]`).
 *
 * Primary route mirrors Zip: the stateless, cookieless, csrf-free streaming screen-load
 * GET (`games/queens/?skipStartScreen=true` with the `x-li-rsc-stream` +
 * `x-li-initial-url` pair). The response is a React Flight stream that today carries the
 * Zip puzzle and may inline the Queens one at any build.
 *
 * GraphQL route: POST `/voyager/api/graphql` with `variables` carrying `gameTypeId:3`
 * (the scraper-observed shape). Session seams ride the shared OkHttp client — the
 * per-process [LinkedInSessionCookieJar] (JSESSIONID from the games-page handshake) and
 * the [LinkedInCsrfInterceptor] (`csrf-token` echo). The extraction, not the URL, decides:
 * [QueensGameExtractor] scans whichever body arrives. [daily] returns a [Response] wrapper
 * so the throttle signature (empty 200 + `location` header) is observable.
 */
interface LinkedInQueensApi {

    /** Primary: the streaming screen-load GET; response is the React Flight stream. */
    @Headers(
        "x-li-rsc-stream: true",
        "x-li-initial-url: /games/queens/",
    )
    @GET("games/queens/")
    suspend fun daily(
        @Query("skipStartScreen") skipStartScreen: Boolean = true,
    ): Response<ResponseBody>

    /** GraphQL rung: `gameTypeId:3` (Queens); [body] is the raw query JSON document. */
    @POST("voyager/api/graphql")
    suspend fun graphql(@Body body: RequestBody): Response<ResponseBody>
}
