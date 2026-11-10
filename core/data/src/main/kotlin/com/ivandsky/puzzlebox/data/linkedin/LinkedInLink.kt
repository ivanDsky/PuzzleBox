package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import kotlinx.coroutines.flow.Flow

/**
 * The one place that knows whether this install is linked to a LinkedIn account and
 * whether reads are paused. Callers go through it rather than the stores behind it, so
 * the pause latch and the disconnect wipe each have a single owner.
 */
interface LinkedInLink {
    val state: Flow<LinkState>

    /** Links [profileId]. A fresh connect is also how a paused link starts reading again. */
    suspend fun connect(profileId: String, consentAtMs: Long)

    /** Persists the latch; every later read returns Paused with no call. */
    suspend fun pause(kind: LinkedInRead.Rejected.Kind)

    /** Lifts the pause only; the link stays. */
    suspend fun resume()

    /** Clears cookies, the LinkedIn database and link state; keeps imports. */
    suspend fun disconnect()
}
