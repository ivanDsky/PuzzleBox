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
 * single source of truth; the holder owns the transient UI overlay state (pending
 * queen move, Auto-check toggle, hint) and the interaction set of A10–A12/A14.
 *
 * Interaction semantics (spec + direction doc):
 * - Tap-cycle on a queen-free cell: empty → MANUAL X (withCrossToggled), X → queen
 *   (withQueenPlaced, A10 auto-fill included). §5.2 haptics are fired by the board.
 * - Tap on a queen: SELECTS it for the A11 move (pending); tap on a second queen
 *   re-selects; tap on the SAME queen again = the cycle's third state — REMOVE
 *   (withQueenRemoved); tap on an empty cell while pending = MOVE (withQueenMoved).
 * - Drag (drag-start + each newly entered cell): MANUAL X on markless queen-free
 *   cells only — idempotent, no flicker over an already-X cell (§5.1).
 * - Undo: session's provenance-aware one-step revert (A12).
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

    /** The pending A11 queen-move source, null when no move is armed. */
    var pendingMoveCell by mutableStateOf<Cell?>(null)
        private set

    /** The current hint presentation (§5.5); cleared by any board action. */
    var hint by mutableStateOf<QueensHint?>(null)
        private set

    /** The last queen move source→target (§5.3 shared-element transition geometry). */
    var lastQueenMove by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** The removal whose AUTO marks exit staggered (§3.3) — transient for animation. */
    var lastRemovedQueen by mutableStateOf<Int?>(null)
        private set

    /** The queen placement whose pop + heavy haptic fire (§3.4/§5.2) — transient. */
    var lastPlacedQueen by mutableStateOf<Int?>(null)
        private set

    /** The A12 undo that restored a moved queen (crossfade-back §5.4) — transient. */
    var lastUndoRestoredMove by mutableStateOf<Pair<Int, Int>?>(null)
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
        lastUndoRestoredMove = null

        if (cell in current.queens.toSet()) {
            val pending = pendingMoveCell
            if (pending == cell) {
                // Third state of the cycle: the queen's own repeat tap removes it.
                pendingMoveCell = null
                lastRemovedQueen = idx
                commit(current.withQueenRemoved(cell, now()))
            } else {
                pendingMoveCell = cell
            }
            return
        }

        val source = pendingMoveCell
        if (source != null) {
            // A11 move: the session clears ONLY the source queen's AUTO marks.
            pendingMoveCell = null
            lastQueenMove = source.idx to idx
            commit(current.withQueenMoved(source, cell, now()))
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

    /** A12: one provenance-aware step; the UI detects a restored move for §5.4. */
    fun undo() {
        pendingMoveCell = null
        hint = null
        val current = session
        val restored = current.withUndo()
        // Detect a restored queen move: the undo snapshot had one more queen than the
        // pre-undo state and the queen set shrank by exactly the source→target pair.
        lastUndoRestoredMove = detectRestoredMove(current, restored)
        commit(restored)
    }

    /** §5.5 hint action: the queens-05 engine's smallest-safe-help payload. */
    fun requestHint() {
        pendingMoveCell = null
        hint = hints.hint(session.puzzle, session.queens)
    }

    /** Clear: remove every placed queen (official-app action; each removal is its
     *  own A12 undo unit, so one undo per queen). */
    fun clearBoard() {
        pendingMoveCell = null
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

    private fun detectRestoredMove(before: QueensSession, after: QueensSession): Pair<Int, Int>? {
        if (before.queens.size - after.queens.size != 1) return null
        val target = before.queens.firstOrNull { it !in after.queens } ?: return null
        val source = after.queens.firstOrNull { it !in before.queens } ?: return null
        return source.idx to target.idx
    }
}
