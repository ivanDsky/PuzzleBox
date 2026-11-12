package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.IdentityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The flag-on scope: the signed-in uid, else the last one that signed in here, so signing out keeps showing the
 * account's rows. This is the only writer of the last uid; the sync engine reads it. It collects from [scope],
 * which the owner of this instance (the app's process scope) controls.
 */
class SignedInOwnerScope(
    private val identity: IdentityService,
    store: SyncStateStore,
    scope: CoroutineScope,
) : OwnerScope {

    override val visible: StateFlow<String?> = combine(identity.current, store.lastUid) { signedIn, last ->
        signedIn?.uid ?: last
    }.stateIn(scope, SharingStarted.Eagerly, identity.current.value?.uid ?: store.lastUid.value)

    init {
        scope.launch {
            identity.current.collect { signedIn -> signedIn?.let { store.setLastUid(it.uid) } }
        }
    }

    override fun writer(): String? = identity.current.value?.uid
}
