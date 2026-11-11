package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.GET
import retrofit2.http.POST

internal interface LinkedInAccountApi {
    @GET("voyager/api/graphql")
    suspend fun read(): String

    @POST("voyager/api/graphql")
    suspend fun write(): String
}
