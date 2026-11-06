package com.ivandsky.puzzlebox.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * NYT's public game JSON endpoints (ADR-0014), addressed by `path` relative to
 * [NYT_BASE_URL], e.g. `svc/strands/v2/2026-11-05.json`. Returns the [Response] wrapper so
 * the adapter can tell a 404 ("no puzzle that day") from every other failure.
 */
internal interface NytApi {

    @GET("{path}")
    suspend fun get(@Path("path", encoded = true) path: String): Response<ResponseBody>
}
