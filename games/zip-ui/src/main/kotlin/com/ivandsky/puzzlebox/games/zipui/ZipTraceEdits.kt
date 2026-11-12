package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.PlayClear
import com.ivandsky.puzzlebox.gameui.PlayUndo
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession

/**
 * The edits to Zip's trace that are not drawing it (A14): undo and redo over the
 * session's command history, and Clear back to the empty trace as one undo unit. Each
 * change is recorded by [log] and goes out through [apply], which commits and persists it
 * like any move; a no-op step changes nothing. A finished trace takes no edits.
 */
internal class ZipTraceEdits(
    private val session: () -> ZipSession?,
    private val log: ZipMoveLog,
    private val apply: (ZipSession) -> Unit,
) : PlayUndo, PlayClear {

    override val canUndo: Boolean
        get() = session()?.let { !it.isComplete && it.history.canUndo } == true

    override val canRedo: Boolean
        get() = session()?.let { !it.isComplete && it.history.canRedo } == true

    override fun undo() = step(ZipReplayMove.Undo)

    override fun redo() = step(ZipReplayMove.Redo)

    /** The trace empties to the start state (R1); the session keeps timer and hints. */
    override fun clear() = step(ZipReplayMove.Reset)

    private fun step(move: ZipReplayMove) {
        val current = session() ?: return
        if (current.isComplete) return
        val next = log.record(current, move)
        if (next !== current) apply(next)
    }
}
