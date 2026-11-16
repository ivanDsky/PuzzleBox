package com.ivandsky.puzzlebox.network.linkedin

import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Tag

/**
 * The requests of a post (ADR-0032): the game page GET and the one write path LinkedIn's web client uses, called
 * for the first move and again for the win. Headers follow the unsolved-daily capture minus what is
 * redacted there or only a browser sends: the trace and instance headers and `sec-*` are left out, not invented.
 * This is the only file that may declare a non-GET, and only this one (Konsist P12).
 *
 * Both bodies are [Streaming]: the caller caps how much it reads, so a hostile page cannot fill memory.
 * The session rides each request as header parameters, as in [LinkedInAccountApi]. `origin` and `referer`
 * name LinkedIn's own web client, not the host the request goes to: they say which page the write came from.
 */
internal interface LinkedInPostApi {
    @Headers(
        "accept: */*",
        "accept-language: $ACCEPT_LANGUAGE",
        "x-li-rsc-stream: true",
        "x-li-application-version: $APPLICATION_VERSION",
        "x-li-anchor-page-key: d_flagship3_search_srp_all",
    )
    @Streaming
    @GET("flagship-web/games/{slug}")
    suspend fun gamePage(
        @Path("slug") slug: String,
        @Header("Cookie") cookie: String,
        @Header("csrf-token") csrfToken: String,
        @Header("User-Agent") userAgent: String,
        @Header("referer") referer: String,
        @Header("x-li-track") track: String,
    ): Response<ResponseBody>

    /** [written] is set once the request body has gone out, so a failure after that is not mistaken for none. */
    @Headers(
        "accept: */*",
        "accept-language: $ACCEPT_LANGUAGE",
        "origin: https://www.linkedin.com",
        "x-li-rsc-stream: true",
        "x-li-application-version: $APPLICATION_VERSION",
    )
    @Streaming
    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun serverRequest(
        @Query("sduiid") sduiid: String,
        @Header("Cookie") cookie: String,
        @Header("csrf-token") csrfToken: String,
        @Header("User-Agent") userAgent: String,
        @Header("referer") referer: String,
        @Header("x-li-anchor-page-key") anchorPageKey: String,
        @Header("x-li-track") track: String,
        @Body body: RequestBody,
        @Tag written: AtomicBoolean,
    ): Response<ResponseBody>
}

private const val ACCEPT_LANGUAGE = "en-US,en;q=0.7"
private const val APPLICATION_VERSION = "0.2.8598"
