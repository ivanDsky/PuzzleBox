package com.ivandsky.puzzlebox.data.visibility

import kotlinx.coroutines.flow.Flow

/** The visibility seam: the player's own tier, live, and any player's tier on demand. */
interface VisibilityRepository {

    /** [OwnTier.Unknown] until the first snapshot (a cached one counts); signed out stays Unknown. */
    val ownTier: Flow<OwnTier>

    suspend fun setOwnTier(tier: VisibilityTier)

    /** One read: a missing doc is `Found(Board)`, an error or offline is [TierLookup.Failed]. */
    suspend fun tierOf(uid: String): TierLookup
}
