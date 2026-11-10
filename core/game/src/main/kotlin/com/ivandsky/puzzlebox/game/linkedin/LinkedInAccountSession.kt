package com.ivandsky.puzzlebox.game.linkedin

/**
 * Session headers for one request. Deliberately not a data class: generated `toString`,
 * `equals`, `hashCode` and `copy` would expose the `li_at` cookie in logs, exception messages
 * and the Compose semantics tree. Compare fields at the call site if sessions ever need comparing.
 */
class LinkedInAccountSession(val cookieHeader: String, val csrfToken: String, val userAgent: String) {
    override fun toString(): String = "LinkedInAccountSession(redacted)"
}
