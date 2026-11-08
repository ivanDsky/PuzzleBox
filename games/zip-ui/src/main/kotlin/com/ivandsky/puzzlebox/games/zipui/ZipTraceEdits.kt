package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.PlayClear
import com.ivandsky.puzzlebox.gameui.PlayUndo
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import java.time.Instant

/**
 * The edits to Zip's trace that are not drawing it (A14): undo and redo over the
 * session's command history, and Clear back to the empty trace as one undo unit. Each
 * change goes out through [apply], which commits and persists it like any move; a no-op
 * step changes nothing. A finished trace takes no edits.
 */
internal class ZipTraceEdits(
    private val session: () -> ZipSession?,
    private val now: () -> Instant,
    private val apply: (ZipSession) -> Unit,
) : PlayUndo, PlayClear {

    override val canUndo: Boolean
        get() = session()?.let { !it.isComplete && it.history.canUndo } == true

    override val canRedo: Boolean
        get() = session()?.let { !it.isComplete && it.history.canRedo } == true

    override fun undo() = step(ZipSession::withUndo)

    override fun redo() = step(ZipSession::withRedo)

    /** The trace empties to the start state (R1); the session keeps timer and hints. */
    override fun clear() = step { it.withMove(ZipMove.Reset, now()) }

    private fun step(edit: (ZipSession) -> ZipSession) {
        val current = session() ?: return
        if (current.isComplete) return
        val next = edit(current)
        if (next !== current) apply(next)
    }
}
