package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.model.PlaySlot

/** The saves of one play: turns a session change into a slot write; flushes or drops it on close. */
internal class PlayPersistence(private val slot: PlaySlot, private val writer: SlotWriter) {

    private var watched: MoveLog? = null
    private var baseline: ByteArray? = null
    private var touched = true

    /**
     * True from [armUntouched] until the player first acts. Acting is the move log growing past what the adopt
     * left in it: the adopt's own session changes and the clock folds change no log, so they never close it.
     * The log is read here, so a move that emits no session change still closes it. A play whose log is off or
     * void cannot tell, so it never counts as untouched.
     */
    val untouched: Boolean
        get() {
            val log = watched ?: return false
            if (!touched && !log.snapshot().contentEquals(baseline)) touched = true
            return !touched
        }

    /** Starts watching [log] for the player's first move; call right after the board is adopted. */
    fun armUntouched(log: MoveLog) {
        baseline = log.snapshot()
        watched = log
        touched = baseline == null
    }

    /** Stops watching: the next board is a new play. */
    fun disarm() {
        watched = null
        touched = true
        baseline = null
    }

    /**
     * Offers a session change to the slot's writer, with the play's in-progress [log] taken in the same turn
     * so the two agree. A void log saves nothing extra: the row stays a version 1 envelope. A finished
     * session never saves: the solve deleted the row.
     */
    fun offer(controller: GamePlayController, session: ByteArray, log: MoveLog) {
        val snapshot = log.snapshot()
        if (controller.isComplete) return
        val elapsedMs = (controller as? PlayClock)?.displayedElapsedMs() ?: 0L
        writer.offer(SessionBlob.encode(slot.gameId, session, snapshot), elapsedMs)
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
