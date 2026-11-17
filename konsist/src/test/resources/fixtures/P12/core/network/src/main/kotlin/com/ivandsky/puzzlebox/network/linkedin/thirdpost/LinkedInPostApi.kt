package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.POST

internal interface LinkedInPostApi {
    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun send(): String

    @POST("voyager/api/graphql")
    suspend fun save(): String

    @POST("voyager/api/other")
    suspend fun third(): String
}
