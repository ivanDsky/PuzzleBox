package com.ivandsky.puzzlebox.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * LinkedIn's unofficial games screen endpoints (research R-19-1 / 02, anonymous; A7 — no
 * sign-in), parameterised by the game's route ([path], e.g. `games/zip/`). The primary
 * route is the screen-load GET LinkedIn's own web client sends: stateless, cookieless,
 * csrf-free — gated only by the `x-li-rsc-stream` + `x-li-initial-url` header pair. The
 * response is a React Flight stream carrying the plain-quoted puzzle object. [stream],
 * [persistedQuery] and [graphql] return a [Response] wrapper so the throttle signature
 * (empty 200 + `location` header) is observable. Parsing is each game's extractor's job —
 * never a pinned path.
 */
internal interface LinkedInGamesApi {

    /** Primary: the streaming screen-load GET; response is the React Flight stream. */
    @GET("{path}")
    suspend fun stream(
        @Path("path", encoded = true) path: String,
        @Header("x-li-initial-url") initialUrl: String,
        @Header("x-li-rsc-stream") rscStream: String = "true",
        @Query("skipStartScreen") skipStartScreen: Boolean = true,
    ): Response<ResponseBody>

    /**
     * Persisted-query rung (ADR-0016; cookie jar + csrf echo). Both params are pre-encoded
     * so Rest.li syntax like `(gameTypeId:7)` reaches LinkedIn unescaped.
     */
    @GET("voyager/api/graphql")
    suspend fun persistedQuery(
        @Query("variables", encoded = true) variables: String,
        @Query("queryId", encoded = true) queryId: String,
    ): Response<ResponseBody>

    /** GraphQL rung (session seams: cookie jar + csrf echo); [body] is the raw query JSON. */
    @POST("voyager/api/graphql")
    suspend fun graphql(@Body body: RequestBody): Response<ResponseBody>

    /** Opportunistic last scan target: the SPA shell (anonymous SSR carries no puzzle today; R-19-1). */
    @GET("{path}")
    suspend fun page(@Path("path", encoded = true) path: String): ResponseBody
}
