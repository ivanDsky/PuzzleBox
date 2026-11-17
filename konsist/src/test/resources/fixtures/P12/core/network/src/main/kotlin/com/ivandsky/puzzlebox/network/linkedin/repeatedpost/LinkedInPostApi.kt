package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.POST

internal interface LinkedInPostApi {
    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun send(): String

    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun again(): String
}
