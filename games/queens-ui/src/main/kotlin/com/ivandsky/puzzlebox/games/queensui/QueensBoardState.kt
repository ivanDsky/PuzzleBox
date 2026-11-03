package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import java.time.Instant

/**
 * The Queens play state holder (ZipPlayState precedent): the [QueensSession] is the
 * single source of truth; the holder owns the transient UI overlay state (Auto-check
 * toggle, hint) and the interaction set of A10–A12/A14.
 *
 * Interaction semantics (spec A11 ruling reversed 2026-11-02 + direction doc):
 * - STRICT tap cycle on EVERY cell: empty → MANUAL X (withCrossToggled), X → queen
 *   (withQueenPlaced, A10 auto-fill included), queen → EMPTY (withQueenRemoved — the
 *   removal takes the queen AND its stored mark, so the cycle closes; fix round 5).
 *   §5.2 haptics are fired by the board.
 * - Drag (drag-start + each newly entered cell): MANUAL X on markless queen-free
 *   cells only — idempotent, no flicker over an already-X cell (§5.1).
 * - Undo: session's provenance-aware one-step revert (A12; one tap = one undo unit).
 * - MANUAL marks are cleared only by their own cell's cycle closing (the queen
 *   removal on the cell that holds them).
 *
 * Win flow (Zip's win→history pattern, F2 §7): the [QueensSession.completedAt]
 * TRANSITION emits ONE [com.ivandsky.puzzlebox.game.CompletionRecord] through the
 * [ScoreModel] + [CompletionRecordEmitter] — fire-and-observe, deduped per emitter
 * lifetime; a restored completed session never re-emits (transition-only).
 */
class QueensBoardState(
    initialSession: QueensSession,
    private val hints: QueensHints = QueensHints(),
    private val score: ScoreModel<QueensSession>? = null,
    private val emitter: CompletionRecordEmitter? = null,
    private val now: () -> Instant = Instant::now,
    private val onSessionChanged: (QueensSession) -> Unit = {},
) {

    var session by mutableStateOf(initialSession)
        private set

    /** A14 Auto-check conflict highlighting — the settings toggle. */
    var autoCheck by mutableStateOf(false)

    /** The current hint presentation (§5.5); cleared by any board action. */
    var hint by mutableStateOf<QueensHint?>(null)
        private set

    /** The removal whose AUTO marks exit staggered (§3.3) — transient for animation. */
    var lastRemovedQueen by mutableStateOf<Int?>(null)
        private set

    /** The queen placement whose pop + heavy haptic fire (§3.4/§5.2) — transient. */
    var lastPlacedQueen by mutableStateOf<Int?>(null)
        private set

    /** Win transition fired exactly once per emitter lifetime. */
    var winEmitted by mutableStateOf(false)
        private set

    //region input

    fun onTap(idx: Int) {
        val current = session
        if (current.isComplete) return
        val cell = Cell(idx)
        if (idx !in 0 until current.puzzle.cellCount) return
        hint = null
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
        hint = null
        commit(current.withCrossToggled(cell, now()))
    }

    /** A12: one provenance-aware step; auto-fill side effects revert with it. */
    fun undo() {
        hint = null
        commit(session.withUndo())
    }

    /** §5.5 hint action: the queens-05 engine's smallest-safe-help payload. */
    fun requestHint() {
        hint = hints.hint(session.puzzle, session.queens)
    }

    /** Clear: remove every placed queen (official-app action; each removal is its
     *  own A12 undo unit, so one undo per queen). */
    fun clearBoard() {
        hint = null
        var current = session
        while (!current.isComplete && current.queens.isNotEmpty()) {
            current = current.withQueenRemoved(current.queens.first(), now())
        }
        commit(current)
    }

    private fun commit(next: QueensSession) {
        val previous = session
        session = next
        onSessionChanged(next)
        if (!previous.isComplete && next.isComplete && !winEmitted) {
            winEmitted = true
            val record = score?.completion(next)
            if (record != null && emitter != null) {
                emitter.emit(record)
            }
        }
    }
}
