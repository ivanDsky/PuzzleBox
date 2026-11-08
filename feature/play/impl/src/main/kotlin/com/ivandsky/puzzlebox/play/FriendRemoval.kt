package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/**
 * Remove with Undo for From friends (spec A19): [remove] hides the code at once and deletes
 * nothing; [removed] names it for [UNDO_WINDOW_MS]. The puzzle and its saved play are deleted
 * ([DeleteReceivedPuzzle]) when that window closes: on expiry, when a newer removal replaces
 * it, or on [clear]. [undo] cancels the delete, so both stay as they were. [scope] outlives
 * the screen, so a closed screen never drops a pending delete.
 */
internal class FriendRemoval(
    private val delete: DeleteReceivedPuzzle,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var window: Job? = null
    private val _removed = MutableStateFlow<ShareCode?>(null)

    /** The code hidden while its Undo is offered, null once restored or deleted. */
    val removed: StateFlow<ShareCode?> = _removed.asStateFlow()

    /** Hides [code] and opens its Undo window; a newer removal deletes the older one now. */
    fun remove(code: ShareCode) {
        if (_removed.value == code) return
        close()
        _removed.value = code
        window = scope.launch {
            delay(UNDO_WINDOW_MS)
            close()
        }
    }

    /** Cancels the pending delete; the puzzle and its saved play stay untouched. */
    fun undo() {
        window?.cancel()
        _removed.value = null
    }

    /** Runs the pending delete now, for the owner going away. */
    fun clear() = close()

    /** Ends the open window and deletes its code; taking the code first makes it run once. */
    private fun close() {
        val code = _removed.getAndUpdate { null } ?: return
        window?.cancel()
        scope.launch { delete(code) }
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
