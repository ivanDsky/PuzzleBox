package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.Draft
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The open draft's saves (spec A5): a trailing throttle like `SlotWriter`'s, not a debounce.
 * The first [offer] opens a [window] and its end stores the newest row, so a run of edits
 * cannot starve the save. [flush] stores the pending row now (app stop, Duplicate, leaving
 * the Studio); [finish] stores it and then takes no more (Delete), so the deleted row is the
 * board the author last saw and no late save brings it back.
 * One write at a time, each taking the newest row when it starts: saves are conflated,
 * never reordered. [saving] is true from an offer until the newest row is stored.
 *
 * Confined to one thread: call it there, and [scope] dispatches there.
 */
internal class DraftAutosave(
    private val scope: CoroutineScope,
    private val store: suspend (Draft) -> Unit,
    private val window: Duration = 300.milliseconds,
) {

    private val writing = Mutex()

    /** The newest row not yet handed to [store]; null when nothing waits. */
    private var pending: Draft? = null

    /** True once [finish] has run: later offers are dropped. */
    private var finished = false

    /** The open throttle window; null when none is open. */
    private var timer: Job? = null

    private val unsaved = MutableStateFlow(false)

    /** "Saving…" while true, "✓ Saved" once the newest row is stored. */
    val saving: StateFlow<Boolean> = unsaved.asStateFlow()

    /** Keeps [draft] as the newest row; the first offer of a window opens it, its end stores the newest. */
    fun offer(draft: Draft) {
        if (finished) return
        pending = draft
        unsaved.value = true
        if (timer != null) return
        timer = scope.launch {
            delay(window)
            timer = null
            write()
        }
    }

    /** Stores the pending row now and returns once it, and any write already running, is stored. */
    suspend fun flush() {
        closeWindow()
        write()
    }

    /** Takes no more offers, then [flush]es: returns once the last offered row is stored. */
    suspend fun finish() {
        finished = true
        flush()
    }

    private fun closeWindow() {
        timer?.cancel()
        timer = null
    }

    private suspend fun write() = writing.withLock {
        val next = pending ?: return@withLock
        pending = null
        store(next)
        if (pending == null) unsaved.value = false
    }
}
