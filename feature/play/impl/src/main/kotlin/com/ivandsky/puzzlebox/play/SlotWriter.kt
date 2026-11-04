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
 * Coalesces one slot's disk saves (A10: latest wins, written within 500 ms). A trailing
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
    /** The newest blob not yet handed to the lane. */
    private var pending: ByteArray? = null

    /** The open throttle window; null when no window is open. */
    private var timer: Job? = null

    /** Keeps [blob] as the latest; the first offer of a window opens it, its end writes the latest. */
    fun offer(blob: ByteArray) {
        pending = blob
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
        val blob = pending ?: return
        pending = null
        lane.enqueue(scope) { saved.save(slot, blob) }
    }
}
