package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.IdentityService

/**
 * "Delete my cloud data" (ADR-0029 decision 8): wipes [uid]'s cloud space for the registered [kinds], then forgets what
 * this install knew about it so a later sign-in uploads everything again. Local rows stay and keep their owner. The
 * engine runs it under its mutex.
 */
class CloudErase(
    private val space: CloudSpace,
    private val kinds: List<SyncedKind>,
    private val store: SyncStateStore,
    private val identity: IdentityService,
) {
    /**
     * Deletes the cloud docs of every kind. On [CloudResult.Ok] each kind forgets its upload markers, the store forgets
     * [uid], and the user is signed out. Any other result is returned as is, with nothing forgotten and no sign-out.
     */
    suspend fun erase(uid: String): CloudResult<Unit> {
        val result = space.deleteAll(uid, kinds.map { it.kind })
        if (result !is CloudResult.Ok) return result
        kinds.forEach { it.forget(uid) }
        store.forget(uid)
        identity.signOut()
        return result
    }
}
