package com.ivandsky.puzzlebox.data.network

import retrofit2.http.GET
import retrofit2.http.Path

/** Feed API exemplar. Phase 1 plugs the real unofficial LinkedIn endpoints into this shape. */
interface PuzzleFeedApi {
    @GET("v1/dailies/{gameId}/{day}")
    suspend fun daily(
        @Path("gameId") gameId: String,
        @Path("day") day: String,
    ): DailyPuzzleDto
}
