package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import com.ivandsky.puzzlebox.games.queensui.hint.QueensStepGame
import kotlinx.coroutines.CoroutineScope

/**
 * The Queens board state holder (ZipPlayController precedent): the [QueensSession] is the
 * single source of truth; the holder owns the transient UI overlay state (Auto-cross
 * toggle, Spotlight hint) and the interaction set of A10–A12/A14.
 *
 * Interaction semantics (spec A11 ruling reversed 2026-11-02 + direction doc):
 * - STRICT tap cycle on EVERY cell: empty → MANUAL X, X → queen (A10 auto-fill when
 *   Auto-cross is ON), queen → EMPTY (the removal takes the queen AND its stored mark,
 *   so the cycle closes; fix round 5). §5.2 haptics are fired by the board.
 * - Drag (drag-start + each newly entered cell): a swipe starting on an empty or queen cell
 *   paints MANUAL X on markless queen-free cells only — idempotent, no flicker over an
 *   already-X cell (§5.1); one painted cell = one command. A swipe starting on a cross
 *   erases the manual crosses it crosses instead, as ONE undo unit.
 * - Undo / Redo: the session's command history (A12; one action = one command, its
 *   auto-fill side effects revert with it). A new action drops the redo stack.
 * - Clear (fix round 6): ONE command that wipes EVERYTHING — queens, AUTO and
 *   MANUAL marks — and one undo restores the queens + manual marks it removed.
 * - MANUAL marks are cleared only by their own cell's cycle closing (or by Clear).
 *
 * Record equals apply: every session change is a [QueensReplayMove] applied through [recorder],
 * which records it, so no input reaches a session reducer any other way. Only [bankElapsed]
 * writes the session directly: banked time is the clock, not a move.
 *
 * Hint: a press shows a [spotlight] through [StepHints] and never places (A1), a culprit is only marked and a second
 * press re-shows it for free (A4), every input clears it in the same call (A10). A newly shown step counts into the
 * session's result facts ([QueensSession.hintsUsed]) as a recorded move; the free re-show records nothing.
 *
 * Clock: the owner (the play state) holds the running window; [settle] is its hook on
 * every committed session — start the window, or fold it into the session's banked
 * [QueensSession.elapsedMs] on a win — BEFORE the session is stored and the win record
 * is built, so the record carries the real duration. [bankElapsed] folds a pause.
 *
 * Guided lesson (spec A12): calling guide() solves the board once and keeps the answer
 * in the [guide] property; the board model then draws a faint crown on every answer
 * square still without a queen and holds back the rule stripes and caption. A daily is
 * never guided.
 *
 * Win flow (Zip's win→result-route pattern, F2 §7): the [QueensSession.completedAt]
 * TRANSITION builds ONE [com.ivandsky.puzzlebox.game.CompletionRecord] through the
 * [ScoreModel] and hands it to [onWinRecord] exactly once per holder lifetime — the
 * flow writes it fire-and-observe and derives the result-route badge; a restored
 * completed session never re-emits (transition-only).
 */
class QueensBoardState(
    initialSession: QueensSession,
    private val score: ScoreModel<QueensSession>? = null,
    private val onWinRecord: (CompletionRecord) -> Unit = {},
    private val recorder: QueensMoveLog = QueensMoveLog(),
    private val onSessionChanged: (QueensSession) -> Unit = {},
    private val settle: (QueensSession) -> QueensSession = { it },
    mode: HintMode.Stepwise,
    scope: CoroutineScope,
) {

    var session by mutableStateOf(initialSession)
        private set

    /** The Auto-cross value the session derives its AUTO marks under: the player's setting, never saved. */
    val autoCross: Boolean get() = session.autoCross

    /** The removal whose AUTO marks exit staggered (§3.3) — transient for animation. */
    var lastRemovedQueen by mutableStateOf<Int?>(null)
        private set

    /** The queen placement whose pop + heavy haptic fire (§3.4/§5.2) — transient. */
    var lastPlacedQueen by mutableStateOf<Int?>(null)
        private set

    /** Win transition fired exactly once per holder lifetime. */
    var winEmitted by mutableStateOf(false)
        private set

    /** A guided lesson's answer squares (row-major); null when the board is not guided. */
    var guide by mutableStateOf<Set<Int>?>(null)
        private set

    internal val stepHints: StepHints<QueensStep, QueensSpotlightMapper.Key> =
        // A newly counted hint: no clearHint on the way, so the Spotlight stays.
        StepHints(mode, scope, QueensStepGame({ session }), { session.hintsUsed }, { commit(QueensReplayMove.Hint) })

    private var erasing = false
    private var eraseRunOpen = false

    /** The Spotlight on screen; null after any input. */
    val spotlight: Spotlight? get() = stepHints.spotlight

    /** Turns on the guided lesson (spec A12): the solver's answer becomes [guide]. */
    fun guide() {
        val answer = checkNotNull(QueensSolver().solve(session.puzzle)) { "a guided Queens board must be solvable" }
        guide = answer.map { it.idx }.toSet()
    }

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
            commit(QueensReplayMove.Remove(cell))
            return
        }

        if (current.markAt(cell) == null) {
            commit(QueensReplayMove.Cross(cell)) // MANUAL X
        } else {
            lastPlacedQueen = idx
            commit(QueensReplayMove.Place(cell)) // X → queen
        }
    }

    /** The swipe's first cell decides its mode: on a cross it erases, anywhere else it paints. */
    fun onDragStart(idx: Int) {
        val current = session
        erasing = idx in 0 until current.puzzle.cellCount && current.markAt(Cell(idx)) != null &&
            Cell(idx) !in current.queens
        eraseRunOpen = false
        onDragCell(idx)
    }

    fun onDragEnd() {
        erasing = false
        eraseRunOpen = false
    }

    fun onDragCell(idx: Int) {
        val current = session
        if (current.isComplete || idx !in 0 until current.puzzle.cellCount) return
        val cell = Cell(idx)
        if (erasing) {
            eraseCross(cell)
            return
        }
        // §5.1 idempotence: no change over a queen or any existing mark (no flicker). A10: the
        // Spotlight clears.
        if (cell in current.queens.toSet() || current.markAt(cell) != null) {
            stepHints.inputMade()
            return
        }
        clearHint()
        commit(QueensReplayMove.Cross(cell))
    }

    private fun eraseCross(cell: Cell) {
        clearHint()
        if (commit(QueensReplayMove.Erase(cell, extend = eraseRunOpen))) eraseRunOpen = true
    }

    /** A12: one command reverted; its auto-fill side effects revert with it. */
    fun undo() {
        clearHint()
        commit(QueensReplayMove.Undo)
    }

    /** Re-apply the most recently undone command. */
    fun redo() {
        clearHint()
        commit(QueensReplayMove.Redo)
    }

    val canUndo: Boolean get() = session.canUndo
    val canRedo: Boolean get() = session.canRedo

    /** A press runs one Spotlight step; it never places a queen or cross (A1). A won board's step is Solved, which shows nothing. */
    fun requestHint() {
        stepHints.press()
    }

    /** Folds [deltaMs] of finished running time into the session's banked clock. */
    fun bankElapsed(deltaMs: Long) {
        if (deltaMs <= 0L) return
        store(session.copy(elapsedMs = session.elapsedMs + deltaMs))
    }

    /** Applies the player's Auto-cross setting (the controller feeds it): ON auto-fills exclusion
     *  marks (A10); OFF places queens bare. Not a board action — no undo unit; the session
     *  re-derives its system marks. An unchanged value touches nothing (no hint clear, no save,
     *  no record: the settings flow echoes every flip made here). */
    fun setAutoCross(enabled: Boolean) {
        if (enabled == session.autoCross) return
        clearHint()
        commit(QueensReplayMove.AutoCross(enabled))
    }

    /** Clear (fix round 6): wipe queens + AUTO + MANUAL marks as ONE undo unit. */
    fun clearBoard() {
        clearHint()
        lastRemovedQueen = null
        lastPlacedQueen = null
        commit(QueensReplayMove.Clear)
    }

    private fun clearHint() {
        stepHints.inputMade()
    }

    /** Applies [move] through the log, then the clock hook, the store and the win. False = a no-op: nothing stored. */
    private fun commit(move: QueensReplayMove): Boolean {
        val previous = session
        val next = recorder.record(previous, move)
        if (next === previous) return false
        val settled = settle(next)
        store(settled)
        if (!previous.isComplete && settled.isComplete && !winEmitted) {
            winEmitted = true
            score?.completion(settled)?.let(onWinRecord)
        }
        return true
    }

    private fun store(next: QueensSession) {
        session = next
        onSessionChanged(next)
    }
}
