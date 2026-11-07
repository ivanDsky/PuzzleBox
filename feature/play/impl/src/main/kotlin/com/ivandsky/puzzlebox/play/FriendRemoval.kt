package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Remove with Undo for From friends (spec A19): [remove] deletes the puzzle and its saved play
 * at once ([DeleteReceivedPuzzle]) and keeps their copies in memory; [undo] writes them back
 * while [removed] still names the code, which it does for [UNDO_WINDOW_MS]. The window lives
 * in [scope], so a closed snackbar never decides what is kept.
 */
internal class FriendRemoval(
    private val received: ReceivedPuzzleRepository,
    private val saved: SavedPlayRepository,
    private val delete: DeleteReceivedPuzzle,
    private val scope: CoroutineScope,
) {
    private class SavedCopy(val blob: ByteArray, val elapsedMs: Long)

    private class Removed(val puzzle: ReceivedPuzzle, val play: SavedCopy?) {
        val slot get() = PlaySlot.Custom(puzzle.gameId, puzzle.code.value)
    }

    private var last: Removed? = null
    private var window: Job? = null
    private val _removed = MutableStateFlow<ShareCode?>(null)

    /** The code whose Undo is offered, null once it is restored or its window closed. */
    val removed: StateFlow<ShareCode?> = _removed.asStateFlow()

    /**
     * Deletes [code]'s puzzle and saved play now and offers Undo for them; a newer removal
     * replaces the older one's Undo. A code with no stored puzzle (a repeat tap) leaves the
     * open window as it is.
     */
    fun remove(code: ShareCode) {
        scope.launch {
            val puzzle = received.get(code) ?: return@launch
            val slot = PlaySlot.Custom(puzzle.gameId, code.value)
            val blob = saved.load(slot)
            val elapsed = saved.observeInProgress().first()[slot]
            delete(code)
            window?.cancel()
            last = Removed(puzzle, if (blob != null && elapsed != null) SavedCopy(blob, elapsed) else null)
            _removed.value = code
            window = scope.launch {
                delay(UNDO_WINDOW_MS)
                forget()
            }
        }
    }

    /** Puts the last removed puzzle back with its saved play, its elapsed time included. */
    fun undo() {
        val shown = last ?: return
        window?.cancel()
        forget()
        scope.launch {
            // The play first, so the card comes back already reading In progress.
            shown.play?.let { saved.save(shown.slot, it.blob, it.elapsedMs) }
            received.upsert(shown.puzzle)
        }
    }

    private fun forget() {
        last = null
        _removed.value = null
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
