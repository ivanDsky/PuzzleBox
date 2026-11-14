package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.IdentityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * The owner scope: the signed-in uid, else the last one that signed in here, so signing out keeps showing the
 * account's rows. Nothing is shown unfiltered while the last uid is still being read.
 * This is the only writer of the last uid; the sync engine reads it. It collects from [scope], which the owner of
 * this instance (the app's process scope) controls.
 */
class SignedInOwnerScope(
    private val identity: IdentityService,
    store: SyncStateStore,
    scope: CoroutineScope,
) : OwnerScope {

    // Signed in, the uid is known at once; signed out, the last uid is known once the store has read it, and until
    // then nothing is emitted, because a null would read as "no filter" and show every account's rows.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val visible: SharedFlow<String?> = identity.current
        .flatMapLatest { signedIn -> if (signedIn != null) flowOf(signedIn.uid) else store.lastUid }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override suspend fun visibleNow(): String? = visible.first()

    init {
        scope.launch {
            identity.current.collect { signedIn -> signedIn?.let { store.setLastUid(it.uid) } }
        }
    }

    override fun writer(): String? = identity.current.value?.uid
}
