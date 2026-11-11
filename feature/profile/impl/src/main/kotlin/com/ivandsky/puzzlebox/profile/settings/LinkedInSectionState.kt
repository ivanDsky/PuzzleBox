package com.ivandsky.puzzlebox.profile.settings

/** What the Settings LinkedIn block draws; the caller derives it from the link, the block reads nothing itself. */
internal sealed interface LinkedInSectionState {
    data object NotConnected : LinkedInSectionState

    data object Connected : LinkedInSectionState

    /**
     * [reason] is plain words, already phrased by the caller. [needsReconnect] is true only after the session
     * was rejected, because resuming a rejected session would just pause again.
     */
    data class Paused(val reason: String, val needsReconnect: Boolean) : LinkedInSectionState
}
