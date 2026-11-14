package com.ivandsky.puzzlebox.data.cloud

import android.util.Log
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.network.NetworkStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * The only thing that starts sync runs besides "Sync now" (A20, ADR-0029 decision 5). While an account is signed in it
 * asks the [engine] for a run when:
 * - the app starts, or the identity signs in (null to a uid, or one uid to another): one run;
 * - the network comes back (the false-to-true edge; the state at sign-in is not an edge);
 * - some kind's pending rows gained a row or a row's content changed (its [SyncedKind.pendingStamps] token), [DEBOUNCE]
 *   after the last such change, so an edit to a row that is already pending, or made during its own upload, starts a
 *   run too. A table emission with the same rows and tokens starts nothing, and a shrinking set is an upload being
 *   acknowledged, so a run never starts another by acknowledging its own rows. What a kind holds when the account
 *   signs in is the sign-in run's to upload, not a change.
 *
 * Signed out, nothing is watched and nothing starts. Signing out cancels nothing in flight: a run lives on [scope]
 * and the engine's uid check ends it. There is no timer, no WorkManager and no listener; the engine coalesces runs
 * that overlap, so every trigger just asks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncRunner(
    private val engine: CloudSync,
    private val kinds: List<SyncedKind>,
    private val identity: IdentityService,
    private val network: NetworkStatus,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch {
            identity.current.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
                if (uid == null) emptyFlow() else triggers(uid)
            }.collect { run() }
        }
    }

    /** Every reason to run for [uid]: the sign-in (or app start) itself, the network coming back, a new or edited pending row. */
    private fun triggers(uid: String): Flow<Unit> = merge(
        flowOf(Unit),
        network.online.distinctUntilChanged().drop(1).filter { it }.map { },
        pendingChanges(uid),
    )

    @OptIn(FlowPreview::class)
    private fun pendingChanges(uid: String): Flow<Unit> {
        val rows = combine(kinds.map { it.pendingStamps(uid) }) { it.toList() }
        return flow {
            var previous: List<Map<String, String>>? = null
            rows.collect { now ->
                val before = previous
                previous = now
                if (before != null && now.indices.any { kind -> now[kind].any { (id, token) -> before[kind][id] != token } }) emit(Unit)
            }
        }.debounce(DEBOUNCE)
    }

    /** A run on the process scope, so it outlives the account change that may cancel the trigger that asked. */
    private fun run() {
        scope.launch {
            try {
                engine.syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync run failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        val DEBOUNCE = 2.seconds
        const val TAG = "CloudSyncRunner"
    }
}
