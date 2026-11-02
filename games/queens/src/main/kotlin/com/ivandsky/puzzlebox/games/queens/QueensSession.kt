package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier
import java.time.Instant

/**
 * Where a cross (exclusion X) mark came from — the provenance the A10–A12 rulings hang on.
 */
enum class MarkKind {
    /** Placed by the user (tap-cycle / drag-to-cross). Survives queen moves and auto-fill. */
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
 * Serializable in-progress play (A10–A12). Every cross mark carries provenance: placing or
 * moving a queen auto-fills exclusion marks over its row/column/region as [MarkKind.AUTO]
 * marks keyed to that queen; they exist only while their source queen stands. MANUAL marks
 * are never cleared by queen moves or auto-fill. One undo step reverts one user action
 * TOGETHER WITH its auto-fill side effects, while manual marks made earlier survive —
 * [undo] holds the pre-action [QueensSnapshot] for exactly that.
 *
 * Win detection (queens-02): all [QueensPuzzle.size] × queensPerUnit queens placed and the
 * placement rule-clean per [QueensVerifier.isSolution]; the moment is stamped [completedAt].
 */
data class QueensSession(
    val ref: QueensPuzzleRef?,
    val puzzle: QueensPuzzle,
    val queens: List<Cell> = emptyList(),
    val marks: Map<Cell, QueensMark> = emptyMap(),
    val undo: List<QueensSnapshot> = emptyList(),
    val completedAt: Instant? = null,
) : GameSession {

    val isComplete: Boolean get() = completedAt != null

    private val occupied: Set<Cell> get() = queens.toSet()

    fun markAt(cell: Cell): QueensMark? = marks[cell]

    /** The required total queen count for this board. */
    val requiredQueens: Int get() = puzzle.size * puzzle.queensPerUnit

    /**
     * A10: place a queen on [cell] (must not already hold a queen). Any mark on the cell is
     * replaced; exclusion marks for the queen's row, column, and region are auto-filled as
     * AUTO marks sourced by this queen — ONLY on cells that carry no mark at all, so MANUAL
     * marks are never overwritten and AUTO marks of other queens stand untouched.
     */
    fun withQueenPlaced(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell.idx !in 0 until puzzle.cellCount || cell in occupied) return this
        var marks = this.marks - cell
        marks = autoFill(marks, source = cell, puzzle = puzzle)
        return commit(
            copy(queens = queens + cell, marks = marks),
            now = now,
        )
    }

    /**
     * A11: move the queen at [from] to the empty cell [to]. The moved queen's AUTO marks are
     * cleared (and only those — MANUAL marks anywhere are never cleared by a move), any mark
     * under the target is replaced, and the new position auto-fills its own exclusions under
     * the same never-overwrite rule.
     */
    fun withQueenMoved(from: Cell, to: Cell, now: Instant): QueensSession {
        if (completedAt != null) return this
        if (from !in occupied || to in occupied || from == to) return this
        if (to.idx !in 0 until puzzle.cellCount) return this
        var marks = dropAutoSourcedBy(this.marks, from) - to
        marks = autoFill(marks, source = to, puzzle = puzzle)
        return commit(
            copy(queens = queens.map { if (it == from) to else it }, marks = marks),
            now = now,
        )
    }

    /**
     * A10 lifecycle: remove the queen at [cell]; its AUTO marks vanish with it. MANUAL marks
     * (including ones previously hidden under auto-fill) are never touched.
     */
    fun withQueenRemoved(cell: Cell, now: Instant): QueensSession {
        if (completedAt != null || cell !in occupied) return this
        val marks = dropAutoSourcedBy(this.marks, cell)
        return commit(
            copy(queens = queens - cell, marks = marks),
            now = now,
        )
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
     * snapshots and survive. No-op when the stack is empty or the game is complete.
     */
    fun withUndo(): QueensSession {
        if (completedAt != null || undo.isEmpty()) return this
        val last = undo.last()
        return copy(queens = last.queens, marks = last.marks, undo = undo.dropLast(1))
    }

    /** Shared tail: snapshot the pre-action state, then stamp a win when the board solves. */
    private fun commit(next: QueensSession, now: Instant): QueensSession {
        val pushed = next.copy(undo = undo + QueensSnapshot(queens, marks))
        return if (QueensVerifier.isSolution(puzzle, pushed.queens.toSet())) {
            pushed.copy(completedAt = now)
        } else {
            pushed
        }
    }

    private fun autoFill(
        marks: Map<Cell, QueensMark>,
        source: Cell,
        puzzle: QueensPuzzle,
    ): Map<Cell, QueensMark> {
        val n = puzzle.size
        val row = puzzle.rowOf(source)
        val col = puzzle.colOf(source)
        val region = puzzle.regionOf(source)
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

    private companion object {
        /** Remove exactly the AUTO marks sourced by [queen]; MANUAL marks are never cleared (A11). */
        fun dropAutoSourcedBy(marks: Map<Cell, QueensMark>, queen: Cell): Map<Cell, QueensMark> =
            marks.filterValues { !(it.kind == MarkKind.AUTO && it.source == queen) }
    }
}
