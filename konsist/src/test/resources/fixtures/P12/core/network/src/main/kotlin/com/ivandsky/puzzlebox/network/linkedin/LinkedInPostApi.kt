package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Mentions `@POST("flagship-web/other")` and `.post(body)` in prose only; comments are not code. */
internal interface LinkedInPostApi {
    @GET("flagship-web/games/zip")
    suspend fun page(): String

    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun send(@Query("sduiid") sduiid: String): String

    @POST("voyager/api/graphql")
    suspend fun save(@Query("action") action: String): String
}
