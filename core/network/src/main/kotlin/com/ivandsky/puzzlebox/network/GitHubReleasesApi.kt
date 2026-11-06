package com.ivandsky.puzzlebox.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

/**
 * GitHub's unauthenticated latest-release endpoint (A2), relative to [GITHUB_API_BASE_URL].
 * Returns the raw [Response] so the adapter parses outside Retrofit and maps every failure to null.
 */
internal interface GitHubReleasesApi {

    @Headers("Accept: application/vnd.github+json")
    @GET("repos/ivanDsky/PuzzleBox/releases/latest")
    suspend fun latest(): Response<ResponseBody>
}
