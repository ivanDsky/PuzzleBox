package com.ivandsky.puzzlebox.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

/** Feed API exemplar. Phase 1 plugs the real unofficial LinkedIn endpoints into this shape. */
interface PuzzleFeedApi {
    /**
     * Returns the raw response body so the repository can store the exact fetched bytes
     * as the opaque envelope payload; the DTO is decoded from those same bytes.
     */
    @GET("v1/dailies/{gameId}/{day}")
    suspend fun daily(
        @Path("gameId") gameId: String,
        @Path("day") day: String,
    ): ResponseBody
}
