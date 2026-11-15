package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

/** LinkedIn's own status for a request it takes for automation. */
private const val LINKEDIN_BLOCKED = 999
private val REDIRECTS = 300..399

/** What a status other than the expected one means, for every LinkedIn call that carries the session. */
internal fun linkedInRejection(code: Int): Kind = when (code) {
    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> Kind.SessionExpired
    LINKEDIN_BLOCKED, in REDIRECTS -> Kind.Challenge
    HTTP_TOO_MANY_REQUESTS -> Kind.RateLimited
    else -> Kind.Unexpected
}
