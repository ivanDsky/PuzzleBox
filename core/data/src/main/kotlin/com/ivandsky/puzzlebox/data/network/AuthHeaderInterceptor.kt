package com.ivandsky.puzzlebox.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Seam for session auth: Phase 1 plugs the LinkedIn csrf/session source in; until then
 * requests go out unsigned via [NONE].
 */
fun interface AuthHeaderSource {
    fun headers(): Map<String, String>

    companion object {
        val NONE = AuthHeaderSource { emptyMap() }
    }
}

class AuthHeaderInterceptor(private val source: AuthHeaderSource) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        for ((name, value) in source.headers()) request.header(name, value)
        return chain.proceed(request.build())
    }
}
