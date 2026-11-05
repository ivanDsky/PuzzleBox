package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces one slot's disk saves (A10: latest wins, written within 500 ms) — each blob with
 * the play's displayed elapsed at that change (the "In progress · m:ss" status). A trailing
 * throttle, not a debounce: the first [offer] opens a [window] and its end writes the
 * latest blob, so continuous moves cannot starve the save. A solve's [discard] supersedes
 * the pending save. Every write goes through the store's shared [lane], so a later writer
 * of the same slot (a release, then a reopen) is ordered after this one.
 *
 * Confined to the store's thread: call it there, and [scope] (the store's lifetime, which
 * outlives a released slot) dispatches there.
 */
internal class SlotWriter(
    private val slot: PlaySlot,
    private val saved: SavedPlayRepository,
    private val lane: WriteLane,
    private val scope: CoroutineScope,
    private val window: Duration = 300.milliseconds,
) {
    /** The newest blob and its elapsed ms, not yet handed to the lane. */
    private var pending: Pending? = null

    /** The open throttle window; null when no window is open. */
    private var timer: Job? = null

    /** The newest blob not yet handed to the lane; null once it is queued (or discarded). */
    val unsaved: ByteArray? get() = pending?.blob

    /** Keeps [blob] at [elapsedMs] as the latest; the first offer of a window opens it, its
     *  end writes the latest. */
    fun offer(blob: ByteArray, elapsedMs: Long) {
        pending = Pending(blob, elapsedMs)
        if (timer != null) return
        timer = scope.launch {
            delay(window)
            timer = null
            drain()
        }
    }

    /** Solve: cancels the pending save and queues the row's delete behind any queued save. */
    fun discard() {
        closeWindow()
        pending = null
        lane.enqueue(scope) { saved.delete(slot) }
    }

    /** Release or app stop: queues the pending save now and waits until the lane has run it
     *  and everything queued before it. */
    suspend fun flush() {
        closeWindow()
        drain()
        lane.run { }
    }

    private fun closeWindow() {
        timer?.cancel()
        timer = null
    }

    private fun drain() {
        val next = pending ?: return
        pending = null
        lane.enqueue(scope) { saved.save(slot, next.blob, next.elapsedMs) }
    }

    private class Pending(val blob: ByteArray, val elapsedMs: Long)
}
