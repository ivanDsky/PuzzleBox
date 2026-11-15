package com.ivandsky.puzzlebox.game.linkedin

/** Outcome of one updateGameState write. */
sealed interface LinkedInSendResult {
    /** 200 with `errors: []`. */
    data object Posted : LinkedInSendResult

    /** 409. */
    data object AlreadyPlayed : LinkedInSendResult

    /** I/O error or timeout after the request was written: it may have landed (A16). Never pauses. */
    data object Unknown : LinkedInSendResult

    /** I/O error or timeout before the request was written; nothing was sent. */
    data object Offline : LinkedInSendResult

    data object SignedOut : LinkedInSendResult

    data class Rejected(val kind: LinkedInRead.Rejected.Kind) : LinkedInSendResult
}
