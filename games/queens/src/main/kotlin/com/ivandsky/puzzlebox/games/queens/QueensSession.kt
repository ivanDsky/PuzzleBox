package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier
import java.time.Duration
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

/** A full board state captured before one user action — the A12 undo unit. */
data class QueensSnapshot(
    val queens: List<Cell>,
    val marks: Map<Cell, QueensMark>,
)

/**
 * Serializable in-progress play (A10–A12; A11 reversed 2026-11-02: queen MOVE is
 * deleted — the interaction is the strict tap cycle empty → X → queen → empty). Every
 * cross mark carries provenance: placing a queen auto-fills exclusion marks over its
 * row/column/region as [MarkKind.AUTO] marks keyed to that queen — while [autoCross]
 * is ON (fix round 6); OFF places queens bare, with no system marks. AUTO marks exist
 * only while their source queen stands AND the setting is ON — they are recompute-
 * derived on every state change. MANUAL marks are never cleared by anything but their
 * own cell's cycling — except Clear, which wipes EVERYTHING (queens + AUTO + MANUAL)
 * as ONE undo unit (round-6 ruling 2026-11-02). One undo step reverts one user action
 * TOGETHER WITH its auto-fill side effects, while manual marks made earlier survive —
 * [undo] holds the pre-action [QueensSnapshot] for exactly that.
 *
 * Win detection (queens-02): all [QueensPuzzle.size] × queensPerUnit queens placed and the
 * placement rule-clean per [QueensVerifier.isSolution]; the moment is stamped [completedAt].
 *
 * Result facts (fix round 6, Zip result-route parity): [startedAt] is stamped by the
 * first committed board action and [hintsUsed] counts presented hints; [elapsedMs]
 * freezes the play duration for the result route and the score record.
 */
data class QueensSession(
    val ref: QueensPuzzleRef?,
    val puzzle: QueensPuzzle,
    val queens: List<Cell> = emptyList(),
    val marks: Map<Cell, QueensMark> = emptyMap(),
    val undo: List<QueensSnapshot> = emptyList(),
    val completedAt: Instant? = null,
    val startedAt: Instant? = null,
    val hintsUsed: Int = 0,
    val autoCross: Boolean = true,
) : GameSession {

    val isComplete: Boolean get() = completedAt != null

    private val occupied: Set<Cell> get() = queens.toSet()

    fun markAt(cell: Cell): QueensMark? = marks[cell]

    /** The required total queen count for this board. */
    val requiredQueens: Int get() = puzzle.size * puzzle.queensPerUnit

    /** Play duration frozen for the result route/score record: first action → win stamp. */
    fun elapsedMs(): Long? {
        val start = startedAt ?: return null
        val end = completedAt ?: return null
        return Duration.between(start, end).toMillis().coerceAtLeast(0L)
    }

    /**
     * A10: place a queen on [cell] (must not already hold a queen). Exclusion marks for
     * the queen's row, column, and region are auto-filled as AUTO marks sourced by this
     * queen — ONLY on cells that carry no MANUAL mark. Marks are recompute-derived from
     * the standing-queen set on every action, so an AUTO mark exists exactly while its
     * source queen stands (A10's invariant, in both directions).
     */
    fun withQueenPlaced(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell.idx !in 0 until puzzle.cellCount || cell in occupied) return this
        return commit(copy(queens = queens + cell), now = now)
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
        return commit(copy(queens = queens - cell, marks = marks - cell), now = now)
    }

    /** Manual tap-cycle cross on a queen-free cell: set, or clear when already MANUAL-marked. */
    fun withCrossToggled(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell in occupied || cell.idx !in 0 until puzzle.cellCount) return this
        val marks = if (marks[cell]?.kind == MarkKind.MANUAL) marks - cell else marks + (cell to QueensMark(MarkKind.MANUAL))
        return commit(copy(marks = marks), now = now)
    }

    /**
     * A12: revert exactly one action together with its auto-fill side effects by restoring
     * the pre-action snapshot; manual marks made before that action are part of earlier
     * snapshots and survive. No-op when the stack is empty or the game is complete. The
     * restored marks are RECOMPUTED under the current [autoCross] setting, so an undo
     * after a setting flip can never resurrect system marks the setting turned off.
     */
    fun withUndo(): QueensSession {
        if (completedAt != null || undo.isEmpty()) return this
        val last = undo.last()
        return copy(
            queens = last.queens,
            marks = recomputeMarks(last.queens, last.marks),
            undo = undo.dropLast(1),
        )
    }

    /**
     * Clear (round-6 ruling 2026-11-02): wipe EVERYTHING — queens, AUTO marks AND
     * MANUAL marks — as ONE undo unit. The pre-clear snapshot [commit] pushes holds the
     * full mark map (MANUAL included), so one undo reverts to exactly the pre-clear
     * board. No-op on a completed session (the win is final; Play again is the way out).
     */
    fun withCleared(now: Instant): QueensSession {
        if (completedAt != null) return this
        return commit(copy(queens = emptyList(), marks = emptyMap()), now = now)
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
        // Recompute UNDER THE NEW setting (the flip's whole point): OFF strips the
        // system marks, ON re-derives them from the standing queens.
        val flipped = copy(autoCross = enabled)
        return flipped.copy(marks = flipped.recomputeMarks(flipped.queens, flipped.marks))
    }

    /**
     * One presented hint counts once (fix round 6 result fact). A hint changes no cell,
     * so it is not an undo unit and does not start the play clock.
     */
    fun withHintUsed(): QueensSession {
        if (completedAt != null) return this
        return copy(hintsUsed = hintsUsed + 1)
    }

    /** Shared tail: recompute AUTO marks from the standing queens, snapshot, stamp a win. */
    private fun commit(next: QueensSession, now: Instant): QueensSession {
        // The play clock starts at the FIRST committed board action (fix round 6).
        val stamped = if (next.startedAt == null) next.copy(startedAt = now) else next
        val recomputed = stamped.copy(marks = recomputeMarks(stamped.queens, stamped.marks))
        val pushed = recomputed.copy(undo = undo + QueensSnapshot(queens, marks))
        return if (QueensVerifier.isSolution(puzzle, pushed.queens.toSet())) {
            pushed.copy(completedAt = now)
        } else {
            pushed
        }
    }

    /**
     * A10, both directions: AUTO marks are DERIVED — exactly the row/column/region
     * exclusions of the standing queens, on cells that carry no MANUAL mark — while
     * [autoCross] is ON (fix round 6; OFF leaves queens bare). MANUAL marks are the
     * only stored truth (including ones hidden under a standing queen); everything
     * AUTO is recomputed, so a mark exists precisely while its source queen stands
     * and the setting is ON, and reappears the moment both do.
     */
    private fun recomputeMarks(queens: List<Cell>, marks: Map<Cell, QueensMark>): Map<Cell, QueensMark> {
        var out = marks.filterValues { it.kind == MarkKind.MANUAL }
        if (!autoCross) return out
        val occupied = queens.toSet()
        for (queen in queens) out = autoFill(out, source = queen, occupied = occupied)
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
            if (idx / n == row || idx % n == col || puzzle.regions[idx] == region) {
                if (cell !in out) out = out + (cell to QueensMark(MarkKind.AUTO, source))
            }
        }
        return out
    }
}
