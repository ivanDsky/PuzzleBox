package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier
import java.time.Instant

/**
 * Where a cross (exclusion X) mark came from — the provenance the A10–A12 rulings hang on.
 */
enum class MarkKind {
    /** Placed by the user (tap-cycle / drag-to-cross). Cleared only by its own cell's cycle. */
    MANUAL,

    /** Recompute-derived auto-fill from a standing queen: exists only while that queen stands. */
    AUTO,
}

/**
 * One cross mark with its provenance. [source] is the queen that produced an [MarkKind.AUTO]
 * mark; null for [MarkKind.MANUAL].
 */
data class QueensMark(val kind: MarkKind, val source: Cell? = null) {
    init {
        require((kind == MarkKind.AUTO) == (source != null)) {
            "AUTO marks carry their source queen, MANUAL marks none"
        }
    }
}

/**
 * Serializable in-progress play (A10–A12; A11 reversed 2026-11-02: queen MOVE is
 * deleted — the interaction is the strict tap cycle empty → X → queen → empty). Every
 * cross mark carries provenance: placing a queen auto-fills exclusion marks over its
 * row/column/region as [MarkKind.AUTO] marks keyed to that queen — while [autoCross]
 * is ON (fix round 6); OFF places queens bare, with no system marks. AUTO marks exist
 * only while their source queen stands AND the setting is ON — they are recompute-
 * derived on every state change. MANUAL marks are never cleared by anything but their
 * own cell's cycling — except Clear, which wipes EVERYTHING (queens + AUTO + MANUAL)
 * as ONE undo unit (round-6 ruling 2026-11-02).
 *
 * Undo/redo (command pattern, 2026-11-03): the BOARD ([QueensBoard]: queens + MANUAL
 * marks — the only stored truth) is edited by [QueensCommand]s kept in [history]; AUTO
 * marks are re-derived after every apply/revert, so one undo step reverts one user
 * action TOGETHER WITH its auto-fill side effects while earlier manual marks survive.
 * [autoCross], [hintsUsed], [elapsedMs] and [completedAt] live OUTSIDE the history:
 * undoing never rewinds a setting, a hint count or the clock. [autoCross] is the runtime
 * input of the derivation only, never saved: the player's per-game setting supplies it.
 *
 * Win detection (queens-02): all [QueensPuzzle.size] × queensPerUnit queens placed and the
 * placement rule-clean per [QueensVerifier.isSolution]; the moment is stamped [completedAt].
 *
 * Result facts: [hintsUsed] counts presented hints; [elapsedMs] is the BANKED play time
 * (Tango parity) — the play state owns the running window and folds it in on pause/win,
 * so this is the frozen duration for the result route and the score record.
 */
data class QueensSession(
    val ref: QueensPuzzleRef?,
    val puzzle: QueensPuzzle,
    val queens: List<Cell> = emptyList(),
    val marks: Map<Cell, QueensMark> = emptyMap(),
    val history: CommandHistory<QueensBoard, QueensCommand> = CommandHistory(),
    val completedAt: Instant? = null,
    val elapsedMs: Long = 0L,
    val hintsUsed: Int = 0,
    val autoCross: Boolean = true,
) : GameSession {

    val isComplete: Boolean get() = completedAt != null

    private val occupied: Set<Cell> get() = queens.toSet()

    fun markAt(cell: Cell): QueensMark? = marks[cell]

    /** The required total queen count for this board. */
    val requiredQueens: Int get() = puzzle.size * puzzle.queensPerUnit

    /** The stored-truth board the commands edit: queens plus MANUAL marks (AUTO is derived). */
    val board: QueensBoard
        get() = QueensBoard(queens, marks.filterValues { it.kind == MarkKind.MANUAL }.keys.toSet())

    val canUndo: Boolean get() = !isComplete && history.canUndo
    val canRedo: Boolean get() = !isComplete && history.canRedo

    /**
     * A10 (round-7 ruling 2026-11-03): place a queen on [cell] (must not already hold a
     * queen). Exclusion marks for the queen's row, column, color region, AND surrounding
     * 3×3 square (all 8 neighbors — mirroring the no-touch rule) are auto-filled as AUTO
     * marks sourced by this queen — ONLY on cells that carry no MANUAL mark. Marks are
     * recompute-derived from the standing-queen set on every action (the union over the
     * standing queens), so an AUTO mark exists exactly while any source queen stands.
     */
    fun withQueenPlaced(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell.idx !in 0 until puzzle.cellCount || cell in occupied) return this
        return commit(QueensCommand.PlaceQueen(cell), now)
    }

    /**
     * A10 lifecycle + A11 strict closure (fix round 5): remove the queen at [cell] AND
     * the mark stored on that cell, so the cell ends EMPTY — the tap cycle closes:
     * empty → X → queen → EMPTY on every cell. The removed queen's AUTO marks vanish
     * because marks are recompute-derived from the standing-queen set; a MANUAL mark
     * hidden under the queen leaves WITH the queen (its own cell's cycling is the only
     * thing that may clear it — spec A11 ruling). MANUAL marks on every other cell are
     * never touched.
     */
    fun withQueenRemoved(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell !in occupied) return this
        val hadMark = marks[cell]?.kind == MarkKind.MANUAL
        return commit(QueensCommand.RemoveQueen(cell, index = queens.indexOf(cell), hadMark = hadMark), now)
    }

    /** Manual tap-cycle cross on a queen-free cell: set, or clear when already MANUAL-marked. */
    fun withCrossToggled(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell in occupied || cell.idx !in 0 until puzzle.cellCount) return this
        return commit(QueensCommand.ToggleCross(cell, wasMarked = marks[cell]?.kind == MarkKind.MANUAL), now)
    }

    /**
     * Clear (round-6 ruling 2026-11-02): wipe EVERYTHING — queens, AUTO marks AND
     * MANUAL marks — as ONE undo unit; the command keeps the wiped queens + manual marks
     * so one undo restores exactly the pre-clear board. No-op on an already-empty board
     * (nothing to wipe, nothing to undo) and on a completed session (the win is final;
     * Play again is the way out).
     */
    fun withCleared(now: Instant): QueensSession {
        if (completedAt != null || (queens.isEmpty() && marks.isEmpty())) return this
        val before = board
        return commit(QueensCommand.Clear(before.queens, before.manual), now)
    }

    /**
     * A12: revert exactly one action together with its auto-fill side effects; manual
     * marks made before that action survive. No-op when the history is empty or the game
     * is complete. Marks are re-derived under the CURRENT [autoCross] setting, so an undo
     * after a setting flip can never resurrect system marks the setting turned off.
     * Hint count, clock and completion are never touched.
     */
    fun withUndo(): QueensSession {
        if (completedAt != null) return this
        val step = history.undo(board) ?: return this
        return withBoard(step.state).copy(history = step.history)
    }

    /** Re-apply the most recently undone action; same guards and derivations as [withUndo]. */
    fun withRedo(): QueensSession {
        if (completedAt != null) return this
        val step = history.redo(board) ?: return this
        return withBoard(step.state).copy(history = step.history)
    }

    /**
     * A14 (fix round 6): the Auto-cross setting — ON auto-fills exclusion marks on
     * placements (A10 as shipped); OFF places queens bare (plain empty→X→queen cycling,
     * no system marks). A SETTINGS flip, not a board action: no undo unit. The standing
     * board recomputes immediately, so flipping OFF strips AUTO marks and flipping ON
     * re-derives them from the standing queens.
     */
    fun withAutoCross(enabled: Boolean): QueensSession {
        if (autoCross == enabled) return this
        // Derive UNDER THE NEW setting (the flip's whole point): OFF strips the system
        // marks, ON re-derives them from the standing queens.
        return copy(autoCross = enabled).withBoard(board)
    }

    /**
     * One presented hint counts once (fix round 6 result fact). A hint changes no cell,
     * so it is not an undo unit.
     */
    fun withHintUsed(): QueensSession {
        if (completedAt != null) return this
        return copy(hintsUsed = hintsUsed + 1)
    }

    /** Shared tail: apply [command], record it, re-derive AUTO marks, stamp a win. */
    private fun commit(command: QueensCommand, now: Instant): QueensSession {
        // Stamps are truncated to epochMilli to MATCH the codec's persistence precision
        // (writeI64 toEpochMilli): an in-memory session must equal its own save/restore
        // round trip (the rotation-restore regression).
        val stamp = Instant.ofEpochMilli(now.toEpochMilli())
        val next = withBoard(command.apply(board)).copy(history = history.record(command))
        return if (QueensVerifier.isSolution(puzzle, next.queens.toSet())) {
            next.copy(completedAt = stamp)
        } else {
            next
        }
    }

    /** This session over [board], with the derived AUTO marks recomputed. */
    private fun withBoard(board: QueensBoard): QueensSession =
        copy(queens = board.queens, marks = derivedMarks(board))

    /**
     * A10, both directions (round-7 ruling 2026-11-03): AUTO marks are DERIVED —
     * exactly the row/column/region/3×3-neighbor exclusions of the standing queens, on
     * cells that carry no MANUAL mark — while [autoCross] is ON (fix round 6; OFF leaves
     * queens bare). MANUAL marks are the only stored truth (including ones hidden under
     * a standing queen); everything AUTO is recomputed as the UNION over the standing
     * queens, so a mark exists precisely while any source queen stands and the setting
     * is ON, and reappears the moment both do.
     */
    private fun derivedMarks(board: QueensBoard): Map<Cell, QueensMark> {
        var out: Map<Cell, QueensMark> = board.manual.associateWith { QueensMark(MarkKind.MANUAL) }
        if (!autoCross) return out
        val occupied = board.queens.toSet()
        for (queen in board.queens) out = autoFill(out, source = queen, occupied = occupied)
        return out
    }

    private fun autoFill(
        marks: Map<Cell, QueensMark>,
        source: Cell,
        occupied: Set<Cell>,
    ): Map<Cell, QueensMark> {
        val n = puzzle.size
        val row = puzzle.rowOf(source)
        val col = puzzle.colOf(source)
        val region = puzzle.regions[source.idx]
        var out = marks
        for (idx in 0 until puzzle.cellCount) {
            val cell = Cell(idx)
            if (cell == source || cell in occupied) continue
            val r = idx / n
            val c = idx % n
            val inUnits = r == row || c == col || puzzle.regions[idx] == region
            // Round-7 ruling (2026-11-03): the fill also covers the surrounding 3×3
            // square — all 8 neighbors, orthogonal + diagonal — mirroring the no-touch
            // rule so the exclusion reads visually.
            val neighbor = kotlin.math.abs(r - row) <= 1 && kotlin.math.abs(c - col) <= 1
            if (inUnits || neighbor) {
                if (cell !in out) out = out + (cell to QueensMark(MarkKind.AUTO, source))
            }
        }
        return out
    }

    companion object {
        /**
         * The codec's way in: a session over a stored [board] with AUTO marks derived
         * under the default [autoCross] (ON); a save never stores the setting, so the
         * caller applies the player's setting with [withAutoCross].
         */
        internal fun restore(
            ref: QueensPuzzleRef?,
            puzzle: QueensPuzzle,
            board: QueensBoard,
            history: CommandHistory<QueensBoard, QueensCommand>,
            completedAt: Instant?,
            elapsedMs: Long,
            hintsUsed: Int,
        ): QueensSession = QueensSession(
            ref = ref,
            puzzle = puzzle,
            history = history,
            completedAt = completedAt,
            elapsedMs = elapsedMs,
            hintsUsed = hintsUsed,
        ).withBoard(board)
    }
}
