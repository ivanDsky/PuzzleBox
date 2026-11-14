package com.ivandsky.puzzlebox.data.visibility

import kotlinx.coroutines.flow.Flow

/** The visibility seam: the player's own tier, live, and any player's tier on demand. */
interface VisibilityRepository {

    /** [OwnTier.Unknown] until the first snapshot (a cached one counts); signed out stays Unknown. */
    val ownTier: Flow<OwnTier>

    /**
     * Stores the player's pick. False when it could not be: nobody is signed in, or the write was refused or failed;
     * never throws for those. Offline the pick is queued and counts as stored.
     */
    suspend fun setOwnTier(tier: VisibilityTier): Boolean

    /** One read: a missing doc is `Found(Board)`, an error or offline is [TierLookup.Failed]. */
    suspend fun tierOf(uid: String): TierLookup
}
