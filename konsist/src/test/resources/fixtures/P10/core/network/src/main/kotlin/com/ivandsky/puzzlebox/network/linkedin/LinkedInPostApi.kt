package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.GET
import retrofit2.http.POST

internal interface LinkedInPostApi {
    @GET("flagship-web/games/zip")
    suspend fun page(): String

    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun send(): String
}
