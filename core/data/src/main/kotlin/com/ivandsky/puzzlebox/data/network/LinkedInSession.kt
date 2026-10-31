package com.ivandsky.puzzlebox.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Anonymous LinkedIn session mechanics (research 01 §2): a GET of the games page sets
 * `JSESSIONID = ajax:<digits>`, and the game POST must echo that value as `csrf-token`.
 * The jar is in-memory on purpose — anonymous means the session is per-process and never
 * persisted (A7); [AuthHeaderSource] stays the seam for a future signed-in phase.
 */
class LinkedInSessionCookieJar : CookieJar {

    private val cookies = LinkedHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this) {
            for (cookie in cookies) this.cookies[key(url, cookie)] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) {
        cookies.values.filter { it.matches(url) }
    }

    /** The `JSESSIONID` value for [url]'s host, or null before the handshake. */
    fun sessionId(url: HttpUrl): String? = synchronized(this) {
        cookies.values.firstOrNull { it.name == "JSESSIONID" && it.matches(url) }?.value
    }

    fun clear() = synchronized(this) { cookies.clear() }

    private fun key(url: HttpUrl, cookie: Cookie): String = "${url.host}|${cookie.name}"
}

/**
 * Adds `csrf-token` (= the session's JSESSIONID value) to requests toward hosts that hold
 * a session. Hosts without a handshake (e.g. MockWebServer before the GET, non-LinkedIn)
 * go out unsigned — the interceptor never fabricates a token.
 */
class LinkedInCsrfInterceptor(private val jar: LinkedInSessionCookieJar) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sessionId = jar.sessionId(request.url)
        val signed = if (sessionId != null) {
            request.newBuilder().header("csrf-token", sessionId).build()
        } else {
            request
        }
        return chain.proceed(signed)
    }
}
