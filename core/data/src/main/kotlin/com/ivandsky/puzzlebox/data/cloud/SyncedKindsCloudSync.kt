package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * under the run, ends it at once with no further call. Any other failure ends it too and the state stays what it was
 * before the run; refining failures into [CloudSyncState.Offline] and [CloudSyncState.Paused] is the failure policy's
 * job. Runs execute on [scope], so they outlive the caller that asked for them. The engine only reads the last uid;
 * `SignedInOwnerScope` writes it.
 */
class SyncedKindsCloudSync(
    private val kinds: List<SyncedKind>,
    private val space: CloudSpace,
    private val store: SyncStateStore,
    private val identity: IdentityService,
    private val erase: CloudErase,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) : CloudSync {

    private val mutex = Mutex()
    private val gate = Any()
    private var queued: CompletableDeferred<Unit>? = null

    private val mutableState = MutableStateFlow<CloudSyncState>(CloudSyncState.SignedOut)
    private val restoreEvents = Channel<RestoreSummary>(Channel.BUFFERED)

    /** Rows restored per kind since this account's restore was last reported; touched only under [mutex]. */
    private val restoredByKind = HashMap<String, Int>()
    private var restoredFor: String? = null

    override val state: StateFlow<CloudSyncState> = mutableState.asStateFlow()

    override val restored: Flow<RestoreSummary> = restoreEvents.receiveAsFlow()

    init {
        scope.launch {
            identity.current.collect { signedIn -> if (signedIn == null) mutableState.value = CloudSyncState.SignedOut }
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
            restoredByKind.clear()
            mutableState.value = CloudSyncState.SignedOut
        }
        result
    }

    private suspend fun runQueued(run: CompletableDeferred<Unit>) {
        mutex.withLock {
            synchronized(gate) { if (queued === run) queued = null }
            try {
                runOnce()
                run.complete(Unit)
            } catch (e: CancellationException) {
                run.completeExceptionally(e)
                throw e
            } catch (e: Throwable) {
                run.completeExceptionally(e)
            }
        }
    }

    private suspend fun runOnce() {
        val uid = identity.current.value?.uid
        if (uid == null) {
            mutableState.value = CloudSyncState.SignedOut
            return
        }
        val before = mutableState.value
        mutableState.value = CloudSyncState.Syncing(firstRestore(uid))
        var outcome: CloudResult<Unit> = CloudResult.Offline
        try {
            outcome = execute(SyncRun(uid, space, store, identity), uid)
        } finally {
            mutableState.value = when {
                identity.current.value == null -> CloudSyncState.SignedOut
                outcome is CloudResult.Ok -> CloudSyncState.Synced(clock.millis())
                else -> before
            }
        }
    }

    private suspend fun execute(run: SyncRun, uid: String): CloudResult<Unit> {
        if (restoredFor != uid) restoredByKind.clear()
        restoredFor = uid
        for (kind in kinds) {
            val adopted = run.adopt(kind)
            if (adopted !is CloudResult.Ok) return adopted
        }
        for (kind in kinds) {
            when (val pulled = run.pull(kind)) {
                is CloudResult.Ok -> restoredByKind.merge(kind.kind, pulled.value, Int::plus)
                CloudResult.Offline -> return CloudResult.Offline
                CloudResult.Exhausted -> return CloudResult.Exhausted
                CloudResult.Denied -> return CloudResult.Denied
                CloudResult.WrongAccount -> return CloudResult.WrongAccount
            }
            val pushed = run.push(kind)
            if (pushed !is CloudResult.Ok) return pushed
        }
        if (!run.signedIn()) return CloudResult.WrongAccount
        reportRestore(uid)
        return CloudResult.Ok(Unit)
    }

    /** True while [uid]'s first restore on this device has not finished: some kind still has no cursor. */
    private suspend fun firstRestore(uid: String): Boolean =
        !store.restoreShown(uid) && kinds.any { store.cursor(uid, it.kind) == null }

    /** After an account's first full run: marks the restore shown and emits what came back, if anything did. */
    private suspend fun reportRestore(uid: String) {
        val counts = restoredByKind.toMap()
        restoredByKind.clear()
        if (store.restoreShown(uid)) return
        store.markRestoreShown(uid)
        val summary = RestoreSummary(
            games = counts[COMPLETIONS] ?: 0,
            drafts = counts[DRAFTS] ?: 0,
            boardDays = counts[BOARDS] ?: 0,
        )
        if (summary.games + summary.drafts + summary.boardDays > 0) restoreEvents.trySend(summary)
    }

    private companion object {
        const val COMPLETIONS = "completions"
        const val DRAFTS = "drafts"
        const val BOARDS = "boards"
    }
}
