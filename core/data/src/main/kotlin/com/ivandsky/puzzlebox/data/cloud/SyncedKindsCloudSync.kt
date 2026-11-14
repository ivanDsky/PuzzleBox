package com.ivandsky.puzzlebox.data.cloud

import android.util.Log
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The sync engine (ADR-0029 decision 4) over the registered [kinds]. One run, for the signed-in uid: every kind's
 * `adopt`; then per kind in list order a pull (`changedSince`, `apply`, cursor to the newest `updatedAtMs`) followed by
 * a push (`pending`, `put`, `acknowledge` on `Ok` only).
 *
 * Runs and [eraseCloud] go through one mutex. [syncNow] calls that arrive while a run is in flight (or while a run is
 * already waiting for the mutex) share exactly one more run. A `WrongAccount` anywhere, including a uid that changed
 * under the run, ends it at once with no further call and keeps the state. A `Denied`, `Offline` or `Exhausted` answer
 * fails only its kind: the next kinds still run, and the run shows the worst failure ([SyncRun.worse]). The write cap
 * ends the run. By [SyncRun.pause] a failed run shows [CloudSyncState.Offline] or [CloudSyncState.Paused]. A paused run
 * retries on its own after the pause's delay (`Exhausted` and `Denied`: 1 min doubling to 30 min; the write cap: the
 * next day; `Offline` the same as `Denied` while [network] is up, since a call that times out says nothing about the
 * network, and not at all while it is down: the network coming back is a trigger). The timer lives on [scope], so it
 * dies with the process, and any trigger runs sooner and replaces it. A failure changes no row, marker or cursor.
 * A run that ends `Ok` with some kind still reporting pending ids (a row edited while it was pending or uploading)
 * is followed by exactly one more run, so the edit never waits for another trigger. Runs execute on [scope], so they
 * outlive the caller that asked for them. A run, or one kind's turn in it, that throws anything but a cancellation (a
 * Room or DataStore failure) is logged by class only and fails like an `Exhausted` answer: [CloudSyncState.Paused] and
 * the normal retry, the other kinds still run. [syncNow] never throws except for its own cancellation. The engine only reads the last uid; `SignedInOwnerScope` writes it.
 *
 * [state] is [CloudSyncState.SignedOut] only while nobody is signed in: a signed-in engine starts at
 * [CloudSyncState.Syncing], the pass the app start is about to run.
 */
class SyncedKindsCloudSync(
    private val kinds: List<SyncedKind>,
    private val space: CloudSpace,
    private val store: SyncStateStore,
    private val identity: IdentityService,
    private val erase: CloudErase,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val network: NetworkStatus,
    private val scope: CoroutineScope,
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) : CloudSync {

    private val mutex = Mutex()
    private val gate = Any()
    private var queued: CompletableDeferred<Unit>? = null

    private val mutableState = MutableStateFlow<CloudSyncState>(CloudSyncState.SignedOut)
    private val restoreEvents = Channel<RestoreSummary>(Channel.BUFFERED)

    /** Rows restored per kind since this account's restore was last reported; touched only under [mutex]. */
    private val restoredByKind = HashMap<String, Int>()
    private var restoredFor: String? = null

    /** The pending automatic retry, if any, and how many cloud pauses in a row [failuresFor] has had; under [mutex]. */
    private var retry: Job? = null
    private var failures = 0
    private var failuresFor: String? = null
    private var lastSynced: Pair<String, Long>? = null

    override val state: StateFlow<CloudSyncState> = mutableState.asStateFlow()

    override val restored: Flow<RestoreSummary> = restoreEvents.receiveAsFlow()

    init {
        scope.launch {
            identity.current.collect { signedIn ->
                if (signedIn == null) {
                    mutableState.value = CloudSyncState.SignedOut
                } else {
                    // A run that started meanwhile has already left SignedOut, so only a still-idle engine changes.
                    mutableState.compareAndSet(CloudSyncState.SignedOut, CloudSyncState.Syncing(firstRestore(signedIn.uid)))
                }
            }
        }
    }

    override suspend fun syncNow() {
        val next = synchronized(gate) {
            queued ?: CompletableDeferred<Unit>().also { run ->
                queued = run
                scope.launch(dispatchers.default) { runQueued(run) }.invokeOnCompletion {
                    synchronized(gate) { if (queued === run) queued = null }
                    run.complete(Unit)
                }
            }
        }
        next.await()
    }

    override suspend fun eraseCloud(): CloudResult<Unit> = mutex.withLock {
        val uid = identity.current.value?.uid ?: return@withLock CloudResult.WrongAccount
        val result = withContext(dispatchers.default) { erase.erase(uid) }
        if (result is CloudResult.Ok) {
            retry?.cancel()
            restoredByKind.clear()
            mutableState.value = CloudSyncState.SignedOut
        }
        result
    }

    private suspend fun runQueued(run: CompletableDeferred<Unit>) {
        mutex.withLock {
            synchronized(gate) { if (queued === run) queued = null }
            try {
                if (runOnce() && hasPending()) runOnce()
                run.complete(Unit)
            } catch (e: CancellationException) {
                run.completeExceptionally(e)
                throw e
            } catch (e: Throwable) {
                warn("sync run failed: ${e.javaClass.simpleName}")
                run.complete(Unit)
            }
        }
    }

    /** Runs the passes once; true when it ended `Ok` for the account that is still signed in. */
    private suspend fun runOnce(): Boolean {
        retry?.cancel()
        retry = null
        val uid = identity.current.value?.uid
        if (uid == null) {
            mutableState.value = CloudSyncState.SignedOut
            return false
        }
        if (failuresFor != uid) {
            failures = 0
            failuresFor = uid
        }
        val before = mutableState.value
        mutableState.value = CloudSyncState.Syncing(firstRestore(uid))
        val run = SyncRun(uid, space, store, identity, clock)
        var finished = false
        var stop: SyncRun.Stop? = null
        var online = false
        try {
            stop = guarded { execute(run, uid) }
            if (stop is SyncRun.Stop.Cloud && stop.result == CloudResult.Offline) online = network.online.first()
            finished = true
        } finally {
            mutableState.value = if (identity.current.value == null) {
                CloudSyncState.SignedOut
            } else if (!finished) {
                before
            } else {
                settle(run, uid, stop, online) ?: before
            }
        }
        return stop == null && run.signedIn()
    }

    /** True while some kind still has pending ids for the signed-in account. */
    private suspend fun hasPending(): Boolean {
        val uid = identity.current.value?.uid ?: return false
        return try {
            kinds.any { kind -> kind.pendingIds(uid).firstOrNull()?.isNotEmpty() == true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            warn("sync pending check failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** [block]'s stop, or an `Exhausted` stop when it threw anything but a cancellation, logging the class only. */
    private suspend fun guarded(block: suspend () -> SyncRun.Stop?): SyncRun.Stop? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        warn("sync run failed: ${e.javaClass.simpleName}")
        SyncRun.Stop.Cloud(CloudResult.Exhausted)
    }

    /** The state after a finished run: [Synced][CloudSyncState.Synced] on success, else [stop]'s pause (null: keep the old state). */
    private fun settle(run: SyncRun, uid: String, stop: SyncRun.Stop?, online: Boolean): CloudSyncState? {
        if (stop == null) {
            failures = 0
            return CloudSyncState.Synced(clock.millis()).also { lastSynced = uid to it.atMs }
        }
        val attempt = failures + 1
        val pause = run.pause(stop, attempt, lastSynced?.takeIf { it.first == uid }?.second, online) ?: return null
        if (pause.retryAfterMs != null && stop is SyncRun.Stop.Cloud) failures = attempt
        pause.retryAfterMs?.let { wait ->
            retry = scope.launch(dispatchers.default) {
                delay(wait)
                syncNow()
            }
        }
        return pause.state
    }

    private suspend fun execute(run: SyncRun, uid: String): SyncRun.Stop? {
        if (restoredFor != uid) restoredByKind.clear()
        restoredFor = uid
        for (kind in kinds) {
            val adopted = run.adopt(kind)
            if (adopted !is CloudResult.Ok) return SyncRun.Stop.Cloud(adopted)
        }
        var worst: SyncRun.Stop? = null
        for (kind in kinds) {
            val stop = guarded { sync(run, kind) } ?: continue
            if (stop.perKind) {
                worst = run.worse(worst, stop)
            } else {
                return if (stop is SyncRun.Stop.WriteCap) run.worse(worst, stop) else stop
            }
        }
        if (!run.signedIn()) return SyncRun.Stop.Cloud(CloudResult.WrongAccount)
        if (worst != null) return worst
        reportRestore(uid)
        return null
    }

    /** One kind's pull, then its push unless the pull failed; null when both went through. */
    private suspend fun sync(run: SyncRun, kind: SyncedKind): SyncRun.Stop? {
        val pulled = run.pull(kind)
        if (pulled !is CloudResult.Ok) return SyncRun.Stop.Cloud(pulled)
        restoredByKind.merge(kind.kind, pulled.value, Int::plus)
        return run.push(kind)
    }

    /** True while [uid]'s first restore on this device has not finished: some kind still has no cursor. */
    private suspend fun firstRestore(uid: String): Boolean = try {
        !store.restoreShown(uid) && kinds.any { store.cursor(uid, it.kind) == null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        warn("sync restore check failed: ${e.javaClass.simpleName}")
        false
    }

    /**
     * After an account's first full run: emits what came back, if anything did, then marks the restore shown. The
     * send comes first so a death between the two repeats nothing but can never lose the welcome.
     */
    private suspend fun reportRestore(uid: String) {
        val counts = restoredByKind.filterValues { it > 0 }
        restoredByKind.clear()
        if (store.restoreShown(uid)) return
        if (counts.isNotEmpty()) restoreEvents.send(RestoreSummary(counts))
        store.markRestoreShown(uid)
    }

    private companion object {
        const val TAG = "SyncedKindsCloudSync"
    }
}
