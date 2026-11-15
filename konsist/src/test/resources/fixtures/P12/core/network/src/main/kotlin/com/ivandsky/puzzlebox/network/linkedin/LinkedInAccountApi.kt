package com.ivandsky.puzzlebox.network.linkedin

import retrofit2.http.GET

internal interface LinkedInAccountApi {
    @GET("voyager/api/graphql")
    suspend fun read(): String
}
