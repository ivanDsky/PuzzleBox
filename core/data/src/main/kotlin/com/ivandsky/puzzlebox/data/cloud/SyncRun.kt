package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.IdentityService

/**
 * The steps of one sync run for [uid] (ADR-0029 decision 4, A4). Every call passes the run's uid explicitly, and every
 * step re-reads the signed-in uid first: once it is no longer [uid] the step does nothing and answers
 * [CloudResult.WrongAccount], so nothing read under one account is ever committed under another. Any other
 * non-[CloudResult.Ok] answer is the cloud's and stops the run; the step has changed nothing past what it reports.
 */
internal class SyncRun(
    private val uid: String,
    private val space: CloudSpace,
    private val store: SyncStateStore,
    private val identity: IdentityService,
) {
    /** True while [uid] is still the signed-in account. */
    fun signedIn(): Boolean = identity.current.value?.uid == uid

    /** Claims [kind]'s ownerless rows for [uid]. */
    suspend fun adopt(kind: SyncedKind): CloudResult<Unit> {
        if (!signedIn()) return CloudResult.WrongAccount
        kind.adopt(uid)
        return CloudResult.Ok(Unit)
    }

    /**
     * Pulls [kind]'s docs since its cursor, applies them, then advances the cursor to the newest `updatedAtMs` seen
     * (the cursor stays when nothing came). Returns how many rows `apply` restored.
     */
    suspend fun pull(kind: SyncedKind): CloudResult<Int> {
        if (!signedIn()) return CloudResult.WrongAccount
        val docs = when (val pulled = space.changedSince(uid, kind.kind, store.cursor(uid, kind.kind))) {
            is CloudResult.Ok -> pulled.value
            CloudResult.Offline -> return CloudResult.Offline
            CloudResult.Exhausted -> return CloudResult.Exhausted
            CloudResult.Denied -> return CloudResult.Denied
            CloudResult.WrongAccount -> return CloudResult.WrongAccount
        }
        if (docs.isEmpty()) return CloudResult.Ok(0)
        if (!signedIn()) return CloudResult.WrongAccount
        val restored = kind.apply(uid, docs)
        docs.mapNotNull { it.updatedAtMs }.maxOrNull()?.let { store.setCursor(uid, kind.kind, it) }
        return CloudResult.Ok(restored)
    }

    /** Uploads [kind]'s pending docs and acknowledges them on [CloudResult.Ok] only. */
    suspend fun push(kind: SyncedKind): CloudResult<Unit> {
        if (!signedIn()) return CloudResult.WrongAccount
        val docs = kind.pending(uid)
        if (docs.isEmpty()) return CloudResult.Ok(Unit)
        val put = space.put(uid, kind.kind, docs)
        if (put is CloudResult.Ok) kind.acknowledge(uid, docs)
        return put
    }
}
