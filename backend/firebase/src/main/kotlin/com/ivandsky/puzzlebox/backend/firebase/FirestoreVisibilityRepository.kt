package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.TierLookup
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [VisibilityRepository] over `visibility/{uid}` = `{tier, v: 1}`. The own tier is a snapshot listener, so the offline
 * cache serves it. A snapshot of a missing doc that only the cache produced says nothing yet (the server may hold a
 * choice), so it is not emitted and the tier stays [OwnTier.Unknown] rather than failing open as Unset. Signed out
 * stays Unknown. [tierOf] reads the server once; a missing doc is Board, an error or timeout is Failed.
 */
internal class FirestoreVisibilityRepository(
    private val identity: StateFlow<Identity?>,
    private val timeout: Duration = CALL_TIMEOUT,
) : VisibilityRepository {

    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun doc(uid: String) = firestore.collection(COLLECTION).document(uid)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val ownTier: Flow<OwnTier> = identity
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(OwnTier.Unknown) else observe(uid).onStart { emit(OwnTier.Unknown) } }

    private fun observe(uid: String): Flow<OwnTier> = callbackFlow {
        val registration = doc(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            when {
                snapshot.exists() -> trySend(OwnTier.Chosen(decodeTier(snapshot.data)))
                !snapshot.metadata.isFromCache -> trySend(OwnTier.Unset)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setOwnTier(tier: VisibilityTier) {
        val uid = checkNotNull(identity.value?.uid) { "setOwnTier needs a signed-in player" }
        // Offline the SDK queues the write and never answers; the cache already holds it, so a timeout is not a failure.
        withTimeoutOrNull(timeout) { doc(uid).set(encodeTierDoc(tier)).await() }
    }

    override suspend fun tierOf(uid: String): TierLookup = try {
        withTimeoutOrNull(timeout) {
            val snapshot = doc(uid).get(Source.SERVER).await()
            TierLookup.Found(if (snapshot.exists()) decodeTier(snapshot.data) else VisibilityTier.Board)
        } ?: TierLookup.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TierLookup.Failed
    }

    private companion object {
        const val COLLECTION = "visibility"
        val CALL_TIMEOUT: Duration = 10.seconds
    }
}

internal const val TIER_FIELD = "tier"
internal const val VERSION_FIELD = "v"
internal const val TIER_DOC_VERSION = 1L

internal fun encodeTierDoc(tier: VisibilityTier): Map<String, Any> =
    mapOf(TIER_FIELD to tier.storageName, VERSION_FIELD to TIER_DOC_VERSION)

/** The stored tier; a doc without a readable tier (missing field, a newer client's value) is Board, never Hidden or higher. */
internal fun decodeTier(fields: Map<String, Any?>?): VisibilityTier =
    VisibilityTier.fromStorageName(fields?.get(TIER_FIELD) as? String) ?: VisibilityTier.Board
