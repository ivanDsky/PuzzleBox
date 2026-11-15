package com.ivandsky.puzzlebox.network.linkedin

import okhttp3.CookieJar
import retrofit2.http.POST

internal interface LinkedInPostApi {
    @POST("flagship-web/rsc-action/actions/server-request")
    suspend fun send(jar: CookieJar): String
}
