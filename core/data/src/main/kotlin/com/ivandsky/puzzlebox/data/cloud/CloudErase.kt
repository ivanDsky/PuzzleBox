package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.IdentityService

/**
 * "Delete my cloud data" (ADR-0029 decision 8): wipes [uid]'s cloud space of every kind in
 * [CloudSpaceKinds], then forgets what this install knew about it so a later sign-in uploads everything again. The wipe
 * does not depend on which [kinds] the install registered (a flag can leave one unbound); only the registered ones have
 * upload markers to forget. Local rows stay and keep their owner. The engine runs it under its mutex.
 */
class CloudErase(
    private val space: CloudSpace,
    private val kinds: List<SyncedKind>,
    private val store: SyncStateStore,
    private val identity: IdentityService,
) {
    /**
     * Deletes the cloud docs of every space kind. On [CloudResult.Ok] each registered kind forgets its upload markers,
     * the store forgets [uid], and the user is signed out. Any other result is returned as is, with nothing forgotten
     * and no sign-out.
     */
    suspend fun erase(uid: String): CloudResult<Unit> {
        val result = space.deleteAll(uid, CloudSpaceKinds.ALL)
        if (result !is CloudResult.Ok) return result
        kinds.forEach { it.forget(uid) }
        store.forget(uid)
        identity.signOut()
        return result
    }
}
