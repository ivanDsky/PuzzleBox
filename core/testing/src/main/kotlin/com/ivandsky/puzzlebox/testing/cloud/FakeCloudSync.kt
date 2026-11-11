package com.ivandsky.puzzlebox.testing.cloud

import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.cloud.RestoreSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scriptable [CloudSync] for UI tests: tests set [state], emit into [restored] with [emitRestored], and read how often
 * `syncNow` and `eraseCloud` were called. `eraseCloud` returns [eraseResult]; it changes nothing else.
 */
class FakeCloudSync(initial: CloudSyncState = CloudSyncState.SignedOut) : CloudSync {

    private val restores = Channel<RestoreSummary>(Channel.UNLIMITED)
    private val syncs = AtomicInteger()
    private val erases = AtomicInteger()

    /** Tests set `state.value`. */
    override val state: MutableStateFlow<CloudSyncState> = MutableStateFlow(initial)

    override val restored: Flow<RestoreSummary> = restores.receiveAsFlow()

    /** What `eraseCloud` returns; set it per test. */
    @Volatile
    var eraseResult: CloudResult<Unit> = CloudResult.Ok(Unit)

    /** How many times `syncNow` was called. */
    val syncNowCalls: Int get() = syncs.get()

    /** How many times `eraseCloud` was called. */
    val eraseCloudCalls: Int get() = erases.get()

    /** Emits [summary] to the collector of [restored]; buffered until one collects. */
    fun emitRestored(summary: RestoreSummary) {
        restores.trySend(summary)
    }

    override suspend fun syncNow() {
        syncs.incrementAndGet()
    }

    override suspend fun eraseCloud(): CloudResult<Unit> {
        erases.incrementAndGet()
        return eraseResult
    }
}
