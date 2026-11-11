package com.ivandsky.puzzlebox.profile.accounts

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView

/** What Profile's LinkedIn row draws; the caller derives it from the link and the phone's stored data, the row reads nothing. */
sealed interface LinkedInRowState {
    data object NotConnected : LinkedInRowState

    data class Connected(val summary: LinkedInAccountView) : LinkedInRowState

    /**
     * [reason] is plain words, already phrased by the caller. [needsReconnect] is true only after the session
     * was rejected, because resuming a rejected session would just pause again.
     */
    data class Paused(
        val summary: LinkedInAccountView,
        val reason: String,
        val needsReconnect: Boolean,
    ) : LinkedInRowState
}
