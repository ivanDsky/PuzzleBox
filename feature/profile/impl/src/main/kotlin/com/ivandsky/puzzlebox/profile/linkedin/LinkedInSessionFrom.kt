package com.ivandsky.puzzlebox.profile.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession

private const val SESSION_COOKIE = "li_at"
private const val CSRF_COOKIE = "JSESSIONID"

/** Pure: the cookie string for https://www.linkedin.com → session, or null without both li_at and JSESSIONID. */
internal fun sessionFrom(cookieString: String?, userAgent: String): LinkedInAccountSession? {
    if (cookieString == null) return null
    if (cookieValue(cookieString, SESSION_COOKIE).isNullOrEmpty()) return null
    // LinkedIn stores the value quoted ("ajax:123…"); the csrf-token header must carry it unquoted.
    val csrfToken = cookieValue(cookieString, CSRF_COOKIE)?.removeSurrounding("\"")
    if (csrfToken.isNullOrEmpty()) return null
    return LinkedInAccountSession(cookieHeader = cookieString, csrfToken = csrfToken, userAgent = userAgent)
}

/** The value of the cookie named exactly [name]: everything after the first `=`, since values may contain `=`. */
private fun cookieValue(cookieString: String, name: String): String? = cookieString.split(';')
    .map { it.trim() }
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter('=')
