package com.ivandsky.puzzlebox.datastore

/**
 * The persisted half of the LinkedIn link; on a fresh install the first four are null and
 * the post log is empty. [pauseReason] and the refs are opaque strings the caller chooses,
 * so this module needs no knowledge of what a pause or a post means.
 */
data class LinkedInLinkPrefs(
    /** Which LinkedIn profile this install is connected to. */
    val profileId: String?,
    /** Epoch millis at which the player accepted the risk warning. */
    val consentAtMs: Long?,
    /** Why reads are paused, or null while they run. */
    val pauseReason: String?,
    /** Epoch millis at which the player accepted the posting risk warning; null means posting is off. */
    val postingConsentAtMs: Long?,
    /** What the linked profile posted from this install. */
    val postedRefs: Set<String> = emptySet(),
    /** What the linked profile began to send and has no known outcome for yet. */
    val sendingRefs: Set<String> = emptySet(),
)
