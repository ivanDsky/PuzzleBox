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

    /**
     * Whether the player turned posting on while linked; false while not connected. A flow of its own, never a field
     * of [LinkState]: the day sync compares link states for equality, and a switch flip must not look like a
     * link change to it.
     */
    val posting: Flow<Boolean>

    /** Links [profileId]. A fresh connect is also how a paused link starts reading again. */
    suspend fun connect(profileId: String, consentAtMs: Long)

    /** Persists the latch; every later read returns Paused with no call. */
    suspend fun pause(kind: LinkedInRead.Rejected.Kind)

    /** Lifts the pause only; the link stays. */
    suspend fun resume()

    /**
     * Turns posting on, recording that the player accepted the posting warning at [consentAtMs], or off with null.
     * A no-op while not connected. A paused link keeps its choice; only [disconnect] forgets it.
     */
    suspend fun setPosting(consentAtMs: Long?)

    /** Clears cookies, the LinkedIn database and link state; keeps imports. */
    suspend fun disconnect()
}
