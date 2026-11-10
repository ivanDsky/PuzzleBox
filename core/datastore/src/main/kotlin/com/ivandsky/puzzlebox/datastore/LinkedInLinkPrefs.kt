package com.ivandsky.puzzlebox.datastore

/**
 * The persisted half of the LinkedIn link; all three are null on a fresh install.
 * [pauseReason] is an opaque string the caller chooses, so this module needs no
 * knowledge of what a pause means.
 */
data class LinkedInLinkPrefs(
    /** Which LinkedIn profile this install is connected to. */
    val profileId: String?,
    /** Epoch millis at which the player accepted the risk warning. */
    val consentAtMs: Long?,
    /** Why reads are paused, or null while they run. */
    val pauseReason: String?,
)
