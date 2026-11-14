package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.data.identity.IdentityService
import java.time.Clock

/**
 * The steps of one sync run for [uid] (ADR-0029 decision 4, A4). Every call passes the run's uid explicitly, and every
 * step re-reads the signed-in uid first: once it is no longer [uid] the step does nothing and answers
 * [CloudResult.WrongAccount], so nothing read under one account is ever committed under another. Any other
 * non-[CloudResult.Ok] answer is the cloud's and stops the run; the step has changed nothing past what it reports.
 *
 * [push] spends the account's daily write cap ([WriteCap]) before each `put`, and [pause] turns a [Stop] into what the
 * engine shows and when it retries ([SyncBackoff]).
 */
internal class SyncRun(
    private val uid: String,
    private val space: CloudSpace,
    private val store: SyncStateStore,
    private val identity: IdentityService,
    private val clock: Clock,
) {
    /** Why a run ended before it finished. */
    sealed interface Stop {
        /** A step answered with [result], which is never [CloudResult.Ok]. */
        data class Cloud(val result: CloudResult<*>) : Stop

        /** The account's daily write cap is spent; some pending docs wait for the next day. */
        data object WriteCap : Stop

        /** A step threw on this phone (a Room or DataStore failure); no cloud answer says why. */
        data object Local : Stop

        /** True when only this kind failed: the run goes on with the next kind (Denied, Offline, Exhausted and Local). */
        val perKind: Boolean
            get() = this == Local ||
                this is Cloud && (result == CloudResult.Offline || result == CloudResult.Exhausted || result == CloudResult.Denied)
    }

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

    /**
     * Uploads [kind]'s pending docs, at most what is left of today's write cap, and acknowledges them on
     * [CloudResult.Ok] only. The batch size is added to today's counter before the `put`. Returns null when every
     * pending doc went up.
     */
    suspend fun push(kind: SyncedKind): Stop? {
        if (!signedIn()) return Stop.Cloud(CloudResult.WrongAccount)
        val pending = kind.pending(uid)
        if (pending.isEmpty()) return null
        val day = WriteCap.day(clock.millis(), clock.zone)
        val room = WriteCap.room(store.countWrites(uid, day, 0))
        if (room == 0) return Stop.WriteCap
        val docs = pending.take(room)
        store.countWrites(uid, day, docs.size)
        val put = space.put(uid, kind.kind, docs)
        if (put !is CloudResult.Ok) return Stop.Cloud(put)
        kind.acknowledge(uid, docs)
        return if (docs.size < pending.size) Stop.WriteCap else null
    }

    /**
     * The pause for [stop] as the [attempt]th consecutive cloud failure; null when [stop] is no reason to pause.
     * [online] is whether the network is up, which decides if an `Offline` answer is retried (see [SyncBackoff.after]).
     */
    fun pause(stop: Stop, attempt: Int, lastAtMs: Long?, online: Boolean): SyncBackoff.Pause? = when (stop) {
        Stop.WriteCap -> SyncBackoff.capped(lastAtMs, WriteCap.msUntilNextDay(clock.millis(), clock.zone))
        Stop.Local -> SyncBackoff.local(attempt, lastAtMs)
        is Stop.Cloud -> SyncBackoff.after(stop.result, attempt, lastAtMs, online)
    }

    /** Whichever of [a] and [b] the run shows: Denied, then Local, Exhausted, Offline, the write cap; null only if both are. */
    fun worse(a: Stop?, b: Stop?): Stop? = if (a == null || b == null) a ?: b else if (rank(b) > rank(a)) b else a

    private fun rank(stop: Stop): Int = when (stop) {
        Stop.WriteCap -> 0
        Stop.Local -> 3
        is Stop.Cloud -> when (stop.result) {
            CloudResult.Offline -> 1
            CloudResult.Exhausted -> 2
            else -> 4
        }
    }
}
