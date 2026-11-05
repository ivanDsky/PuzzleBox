package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import java.time.Instant

/**
 * The Queens board state holder (ZipPlayController precedent): the [QueensSession] is the
 * single source of truth; the holder owns the transient UI overlay state (Auto-cross
 * toggle, staged hint) and the interaction set of A10–A12/A14.
 *
 * Interaction semantics (spec A11 ruling reversed 2026-11-02 + direction doc):
 * - STRICT tap cycle on EVERY cell: empty → MANUAL X (withCrossToggled), X → queen
 *   (withQueenPlaced, A10 auto-fill when Auto-cross is ON), queen → EMPTY
 *   (withQueenRemoved — the removal takes the queen AND its stored mark, so the cycle
 *   closes; fix round 5). §5.2 haptics are fired by the board.
 * - Drag (drag-start + each newly entered cell): MANUAL X on markless queen-free
 *   cells only — idempotent, no flicker over an already-X cell (§5.1). One painted
 *   cell = one command (no gesture coalescing).
 * - Undo / Redo: the session's command history (A12; one action = one command, its
 *   auto-fill side effects revert with it). A new action drops the redo stack.
 * - Clear (fix round 6): ONE command that wipes EVERYTHING — queens, AUTO and
 *   MANUAL marks — and one undo restores the queens + manual marks it removed.
 * - MANUAL marks are cleared only by their own cell's cycle closing (or by Clear).
 *
 * Staged hint ladder (§5.5, fix round 6): the first tap presents the queens-05
 * engine's smallest-safe-help payload with its DEDUCTION caption (or the neutral
 * fallback when the forcing is solver-only); a repeat tap on the same forced cell
 * escalates to stage 2 — [hintPointed] renders the crown ghost directly on the cell.
 * Any board action clears the hint. A NEW presented hint counts into the session's
 * result facts ([QueensSession.hintsUsed]); the stage-2 repeat is presentation only.
 *
 * Clock: the owner (the play state) holds the running window; [settle] is its hook on
 * every committed session — start the window, or fold it into the session's banked
 * [QueensSession.elapsedMs] on a win — BEFORE the session is stored and the win record
 * is built, so the record carries the real duration. [bankElapsed] folds a pause.
 *
 * Win flow (Zip's win→result-route pattern, F2 §7): the [QueensSession.completedAt]
 * TRANSITION builds ONE [com.ivandsky.puzzlebox.game.CompletionRecord] through the
 * [ScoreModel] and hands it to [onWinRecord] exactly once per holder lifetime — the
 * flow writes it fire-and-observe and derives the result-route badge; a restored
 * completed session never re-emits (transition-only).
 */
class QueensBoardState(
    initialSession: QueensSession,
    private val hints: QueensHints = QueensHints(),
    private val score: ScoreModel<QueensSession>? = null,
    private val onWinRecord: (CompletionRecord) -> Unit = {},
    private val now: () -> Instant = Instant::now,
    private val onSessionChanged: (QueensSession) -> Unit = {},
    private val settle: (QueensSession) -> QueensSession = { it },
) {

    var session by mutableStateOf(initialSession)
        private set

    /** A14 (fix round 6): the Auto-cross setting rides the SESSION (codec-persisted). */
    val autoCross: Boolean get() = session.autoCross

    /** The current hint presentation (§5.5); cleared by any board action. */
    var hint by mutableStateOf<QueensHint?>(null)
        private set

    /** Stage 2 of the ladder: the repeat tap points at the same forced cell. */
    var hintPointed by mutableStateOf(false)
        private set

    /** The removal whose AUTO marks exit staggered (§3.3) — transient for animation. */
    var lastRemovedQueen by mutableStateOf<Int?>(null)
        private set

    /** The queen placement whose pop + heavy haptic fire (§3.4/§5.2) — transient. */
    var lastPlacedQueen by mutableStateOf<Int?>(null)
        private set

    /** Win transition fired exactly once per holder lifetime. */
    var winEmitted by mutableStateOf(false)
        private set

    private var lastHintCellIdx: Int? = null

    //region input

    fun onTap(idx: Int) {
        val current = session
        if (current.isComplete) return
        val cell = Cell(idx)
        if (idx !in 0 until current.puzzle.cellCount) return
        clearHint()
        lastRemovedQueen = null
        lastPlacedQueen = null

        if (cell in current.queens.toSet()) {
            // The cycle's third state on a queen cell: remove (queen → empty).
            lastRemovedQueen = idx
            commit(current.withQueenRemoved(cell, now()))
            return
        }

        if (current.markAt(cell) == null) {
            commit(current.withCrossToggled(cell, now())) // MANUAL X
        } else {
            lastPlacedQueen = idx
            commit(current.withQueenPlaced(cell, now())) // X → queen
        }
    }

    fun onDragCell(idx: Int) {
        val current = session
        if (current.isComplete || idx !in 0 until current.puzzle.cellCount) return
        val cell = Cell(idx)
        // §5.1 idempotence: no change over a queen or any existing mark (no flicker).
        if (cell in current.queens.toSet() || current.markAt(cell) != null) return
        clearHint()
        commit(current.withCrossToggled(cell, now()))
    }

    /** A12: one command reverted; its auto-fill side effects revert with it. */
    fun undo() {
        clearHint()
        commit(session.withUndo())
    }

    /** Re-apply the most recently undone command. */
    fun redo() {
        clearHint()
        commit(session.withRedo())
    }

    val canUndo: Boolean get() = session.canUndo
    val canRedo: Boolean get() = session.canRedo

    /** The §5.5 caption of the active hint, null when there is none (PlayHints seam). */
    val hintCaption: String? get() = queensHintCaption(hint)

    /**
     * §5.5 staged ladder: first tap presents the hint (stage 1); a repeat tap while the
     * engine still forces the SAME cell escalates to stage 2 ([hintPointed] — the crown
     * ghost). A new presentation counts once into [QueensSession.hintsUsed]; the stage-2
     * repeat does not. The hint changes no cell, so it is not an undo unit.
     */
    fun requestHint() {
        val next = hints.hint(session.puzzle, session.queens)
        val regionCell = (next as? QueensHint.Region)?.cell
        val repeat = regionCell != null && regionCell.idx == lastHintCellIdx
        lastHintCellIdx = regionCell?.idx
        hint = next
        hintPointed = repeat
        if (!repeat && next != QueensHint.None && !session.isComplete) {
            store(settle(session.withHintUsed()))
        }
    }

    /** Folds [deltaMs] of finished running time into the session's banked clock. */
    fun bankElapsed(deltaMs: Long) {
        if (deltaMs <= 0L) return
        store(session.copy(elapsedMs = session.elapsedMs + deltaMs))
    }

    /** A14 (fix round 6): ON auto-fills exclusion marks (A10); OFF places queens bare.
     *  A settings flip — no undo unit; the session recomputes its system marks. */
    fun setAutoCross(enabled: Boolean) {
        clearHint()
        val next = session.withAutoCross(enabled)
        if (next == session) return
        store(next)
    }

    /** Clear (fix round 6): wipe queens + AUTO + MANUAL marks as ONE undo unit. */
    fun clearBoard() {
        clearHint()
        lastRemovedQueen = null
        lastPlacedQueen = null
        commit(session.withCleared(now()))
    }

    private fun clearHint() {
        hint = null
        hintPointed = false
        lastHintCellIdx = null
    }

    private fun commit(next: QueensSession) {
        if (next == session) return // a no-op action: no history, no clock, no persist churn
        val previous = session
        val settled = settle(next)
        store(settled)
        if (!previous.isComplete && settled.isComplete && !winEmitted) {
            winEmitted = true
            score?.completion(settled)?.let(onWinRecord)
        }
    }

    private fun store(next: QueensSession) {
        session = next
        onSessionChanged(next)
    }
}

/**
 * The §5.5 caption text for the active hint (fix round 6). The region is always named
 * by its palette color (the pulsed fill); the deduction names the unit whose every
 * other cell is blocked. null = nothing to show (no hint, or a no-op hint).
 */
internal fun queensHintCaption(hint: QueensHint?): String? = when (hint) {
    is QueensHint.Region -> {
        val color = QueensRegionPalette.hueName(hint.region)
        when (hint.reason?.kind) {
            HintReason.Kind.ROW -> "The $color queen goes in this row. The other cells are blocked."
            HintReason.Kind.COLUMN -> "The $color queen goes in this column. The other cells are blocked."
            HintReason.Kind.REGION -> "The $color queen goes here. The other $color cells are blocked."
            // Solver-only forcing: the honest fallback (never a fabricated reason).
            null -> "Hint — the $color region"
        }
    }
    is QueensHint.Incorrect -> "Some of these queens can't stay. Take one away."
    QueensHint.None, null -> null
}
