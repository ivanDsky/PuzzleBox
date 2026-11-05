package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.PlayFeedback
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation.Reason
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Zip's in-play messages (ticket 06, spec A7): the refused drag and the hint's trim, as
 * Compose state the shared caption slot observes. [rejectedMove] is the board's brief
 * rejection highlight (cleared after [MotionTokens.RejectHoldMillis]); [rejection] is its readable
 * copy, kept until the next accepted move so the slot does not flicker. Zip has no
 * standing rule break — a trace is legal by construction — so [violation] is always null.
 */
class ZipFeedback(private val scope: CoroutineScope) : PlayFeedback {

    var rejectedMove by mutableStateOf<ZipRendererModel.RejectedMove?>(null)
        private set

    override var rejection by mutableStateOf<String?>(null)
        private set

    /** The hint's explanation: what the free trim fixed (null = the arrow says it all). */
    var hintCaption by mutableStateOf<String?>(null)
        private set

    override val violation: String? get() = null

    private val rules = zipPlugin.rules as ZipRules // the plugin's rules ARE ZipRules

    fun reject(board: ZipBoard, cell: Cell, reason: Reason) {
        rejectedMove = ZipRendererModel.RejectedMove(cell.idx, reason)
        rejection = rejectionCopy(board, reason)
        scope.launch {
            delay(MotionTokens.RejectHoldMillis)
            if (rejectedMove?.cellIdx == cell.idx) rejectedMove = null
        }
    }

    /**
     * The drag board refused [cell] before any move existed (DragEvent.Refused). Only a
     * refusal the player plausibly meant — skipping a number, crossing a wall, starting
     * off 1 — is explained; a finger wandering onto a far or traced cell stays silent
     * (R1), and nothing here is a mistake for analytics.
     */
    fun refused(session: ZipSession, cell: Cell) {
        val verdict = rules.validateMove(ZipMove.Extend(cell), ZipState(session.puzzle, session.path))
        val reason = (verdict as? ZipValidation.Invalid)?.reason ?: return
        if (reason is Reason.WrongWaypointOrder || reason == Reason.WallBetween || reason == Reason.WrongStart) {
            reject(session.puzzle.board, cell, reason)
        }
    }

    /** The hint trimmed a dead trace back to [kept] (empty = back to the start). */
    fun trimmed(board: ZipBoard, kept: List<Cell>) {
        val reached = kept.mapNotNull(board::waypointLabel).maxOrNull()
        hintCaption = if (reached == null) "Started your path over" else "Fixed your path up to $reached"
    }

    /** An accepted move (or a hint's own commit) retires every message. */
    fun onAccepted() {
        rejection = null
        hintCaption = null
    }

    fun clear() {
        rejectedMove = null
        onAccepted()
    }

    private fun rejectionCopy(board: ZipBoard, reason: Reason): String? = when (reason) {
        is Reason.WrongWaypointOrder -> board.waypointLabel(reason.expectedNext)?.let { "Go to $it first" }
        Reason.WrongStart -> "Start at 1"
        Reason.NotAdjacent -> "Move to a square next to it"
        Reason.WallBetween -> "A wall blocks that way"
        Reason.CellAlreadyInPath -> "Your line already goes through that square"
        Reason.OutOfBounds, Reason.NotInPath, Reason.ForeignMove -> null
    }
}
