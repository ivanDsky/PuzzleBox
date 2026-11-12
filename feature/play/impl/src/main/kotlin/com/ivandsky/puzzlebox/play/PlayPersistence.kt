package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.model.PlaySlot

/** The saves of one play: turns a session change into a slot write; flushes or drops it on close. */
internal class PlayPersistence(private val slot: PlaySlot, private val writer: SlotWriter) {

    /** Offers a session change to the slot's writer. A finished session never saves: the solve deleted the row. */
    fun offer(controller: GamePlayController, session: ByteArray) {
        if (controller.isComplete) return
        val elapsedMs = (controller as? PlayClock)?.displayedElapsedMs() ?: 0L
        writer.offer(SessionBlob.encode(slot.gameId, session), elapsedMs)
    }

    /** App stop: folds the clock and waits until the slot's pending save is written. */
    suspend fun save(controller: GamePlayController) {
        fold(controller)
        writer.flush()
    }

    /** Drops the pending save. */
    fun discard() = writer.discard()

    /** The newest session change the writer still holds (not yet queued for disk). */
    val unsaved: ByteArray? get() = writer.unsaved

    /** Banks the running clock window so the next save carries it. */
    private fun fold(controller: GamePlayController) {
        (controller as? PlayClock)?.onBackground()
    }
}
