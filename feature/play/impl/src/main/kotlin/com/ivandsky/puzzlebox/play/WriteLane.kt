package com.ivandsky.puzzlebox.play

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The slot store's single serial queue for saved-play disk I/O (A10, A11). Every save,
 * delete and opening read runs through ONE lane, in call order, one at a time.
 *
 * Its forcing function (patterns-7): a slot released with a pending final save, then
 * reopened with a new writer, must read that save. Per-slot writers cannot order across
 * writer lifetimes, and `limitedParallelism(1)` does not serialize suspending Room calls
 * (a suspended call frees the dispatcher for the next one). A fair [Mutex] does: it is
 * granted first come, first served and held across the op's suspensions.
 */
internal class WriteLane {

    private val mutex = Mutex()

    /** Runs [op] after every op queued before it and returns its result (opening reads). */
    suspend fun <T> run(op: suspend () -> T): T = mutex.withLock { op() }

    /**
     * Queues [op] in [scope] without suspending the caller (saves and deletes). The op
     * takes its place in the lane before this returns, so ops enqueued one after another
     * run in that order whatever their scopes.
     */
    fun enqueue(scope: CoroutineScope, op: suspend () -> Unit): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) { run(op) }
}
