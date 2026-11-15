package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.POST

internal interface LinkedInPostApi {
    @POST("flagship-web/rsc-action/actions/other")
    suspend fun send(): String
}
