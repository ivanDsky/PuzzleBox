package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.TierLookup
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [VisibilityRepository]: starts [OwnTier.Unknown]; script other players' tiers and failures. */
class FakeVisibilityRepository(initial: OwnTier = OwnTier.Unknown) : VisibilityRepository {

    private val own = MutableStateFlow(initial)
    private val others = mutableMapOf<String, VisibilityTier>()

    /** uids whose [tierOf] fails. */
    val failingUids = mutableSetOf<String>()

    val setCalls = mutableListOf<VisibilityTier>()
    val lookupCalls = mutableListOf<String>()

    /** False: nobody is signed in, so [setOwnTier] stores nothing and answers false. */
    var signedIn = true

    override val ownTier: Flow<OwnTier> = own.asStateFlow()

    /** Simulates a snapshot arriving. */
    fun emitOwn(value: OwnTier) {
        own.value = value
    }

    /** Stores [tier] for [uid]; a uid never stored reads as Board, like a missing doc. */
    fun putTier(uid: String, tier: VisibilityTier) {
        others[uid] = tier
    }

    override suspend fun setOwnTier(tier: VisibilityTier): Boolean {
        if (!signedIn) return false
        setCalls += tier
        own.value = OwnTier.Chosen(tier)
        return true
    }

    override suspend fun tierOf(uid: String): TierLookup {
        lookupCalls += uid
        if (uid in failingUids) return TierLookup.Failed
        return TierLookup.Found(others[uid] ?: VisibilityTier.Board)
    }
}
