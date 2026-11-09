package com.ivandsky.puzzlebox.gameui.hint

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one hint search off the main thread and drops a stale result (spec A19, A10): a late hint
 * never lands on a board it was not computed for.
 *
 * Call [request] and [invalidate] from [scope]'s dispatcher (the controller's main thread). A
 * throwing compute is not swallowed: it surfaces on [scope]'s exception handler and nothing is
 * delivered. Cancelling cannot interrupt a compute already running (it is a plain lambda); it only
 * stops the delivery, and the engine budget (A19) bounds the wasted work.
 */
class HintSearch(private val scope: CoroutineScope, compute: CoroutineDispatcher) {
    private val computeDispatcher = compute
    private var pending: Job? = null

    /** Cancels the previous request; runs [compute] on the compute dispatcher; [deliver] runs on the scope's
     *  dispatcher only if no invalidate()/request() happened since (A19). */
    fun <S> request(compute: () -> S, deliver: (S) -> Unit) {
        pending?.cancel()
        pending = scope.launch {
            // withContext's cancellation is prompt: a job cancelled while computing, or after the result
            // was handed back but before this resumed, throws here instead of returning the result.
            val result = withContext(computeDispatcher) { compute() }
            deliver(result)
        }
    }

    /** The board changed: a result still computing, or computed but not yet delivered, is dropped. */
    fun invalidate() {
        pending?.cancel()
        pending = null
    }
}
