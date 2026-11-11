package com.ivandsky.puzzlebox.network.linkedin

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * The one Voyager endpoint the account feature reads. GET only: the feature never writes to LinkedIn.
 *
 * The session rides each request as header parameters, so the client holds nothing between calls. The
 * query parameters are declared in the order LinkedIn's own web client sends them, and `variables` and
 * `queryId` go out as written: their tuple syntax must reach the wire unescaped.
 */
internal interface LinkedInAccountApi {
    @Headers(
        "accept: application/vnd.linkedin.normalized+json+2.1",
        "x-restli-protocol-version: 2.0.0",
        "x-li-lang: en_US",
    )
    @GET("voyager/api/graphql")
    suspend fun graphql(
        @Header("Cookie") cookie: String,
        @Header("csrf-token") csrfToken: String,
        @Header("User-Agent") userAgent: String,
        @Query("includeWebMetadata") includeWebMetadata: Boolean,
        @Query("variables", encoded = true) variables: String,
        @Query("queryId", encoded = true) queryId: String,
    ): Response<ResponseBody>
}
