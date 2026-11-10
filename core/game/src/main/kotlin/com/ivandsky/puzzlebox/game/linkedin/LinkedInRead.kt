package com.ivandsky.puzzlebox.game.linkedin

/**
 * The one result type of every LinkedIn account call. Only [Ok] carries data; the rest say
 * why there is none, so callers never confuse a network failure with a rejected session.
 */
sealed interface LinkedInRead<out T> {
    data class Ok<T>(val value: T) : LinkedInRead<T>

    /** I/O error or timeout. Not a rejection: the session may still be good. */
    data object Offline : LinkedInRead<Nothing>

    /** No session cookies in the store; no request was sent. */
    data object SignedOut : LinkedInRead<Nothing>

    /** Anything other than a 200 with JSON of the expected shape. Trips the pause latch. */
    data class Rejected(val kind: Kind) : LinkedInRead<Nothing> {
        enum class Kind {
            /** 401, 403. Only a fresh connect clears it. */
            SessionExpired,
            /** 999, any redirect, a non-JSON 200. */
            Challenge,
            /** 429. */
            RateLimited,
            /** Any other 4xx/5xx, or a 200 whose JSON has the wrong shape (covers query id drift). */
            Unexpected,
        }
    }
}
