package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/** The rules state: the puzzle plus the player's current trace ([path][0] = start). */
data class ZipState(
    val puzzle: ZipPuzzle,
    val path: List<Cell>,
)

/** Game-authored moves (drag steps, drag-backs, tap-ahead, reset). */
sealed interface ZipMove : Move {
    /** Append the next cell — from a drag step or an adjacent tap (F2 §10). */
    data class Extend(val to: Cell) : ZipMove

    /** Trim the trace back to an already-visited cell (drag-back over the path). */
    data class RetractTo(val to: Cell) : ZipMove

    data object Reset : ZipMove
}

/** Game-authored validation outcome; [Reason] drives the rejection animation + mistakes. */
sealed interface ZipValidation : MoveVerdict {
    data object Valid : ZipValidation
    data class Invalid(val reason: Reason) : ZipValidation

    sealed interface Reason {
        data object NotAdjacent : Reason
        data object WallBetween : Reason
        data object CellAlreadyInPath : Reason
        data object WrongStart : Reason
        data class WrongWaypointOrder(val expectedNext: Cell) : Reason
        data object OutOfBounds : Reason

        /** RetractTo target is not on the trace (defensive; the UI cannot produce it). */
        data object NotInPath : Reason
    }
}

/**
 * Zip rules (F2 §2): O(1) local checks per move (I1–I5, I8), completeness (I6/I7), and
 * solver-backed hints counted by the caller (A3). Pure — never mutates state.
 */
class ZipRules(
    @Suppress("unused") private val variants: Map<com.ivandsky.puzzlebox.game.VariantId, ZipVariant> = ZipVariants.table,
    private val hints: ZipHints = ZipHints(),
) : GameRules<ZipState> {

    override fun validate(move: Move, state: ZipState): MoveVerdict =
        validateMove(move as ZipMove, state)

    fun validateMove(move: ZipMove, state: ZipState): ZipValidation {
        val board = state.puzzle.board
        val path = state.path
        return when (move) {
            is ZipMove.Reset -> ZipValidation.Valid

            is ZipMove.RetractTo -> {
                val index = path.indexOf(move.to)
                when {
                    index < 0 -> ZipValidation.Invalid(ZipValidation.Reason.NotInPath)
                    else -> ZipValidation.Valid // trim to the tail itself is a no-op
                }
            }

            is ZipMove.Extend -> {
                if (!board.contains(move.to)) return ZipValidation.Invalid(ZipValidation.Reason.OutOfBounds)
                if (path.isEmpty()) {
                    return if (move.to == board.waypoints.first()) {
                        ZipValidation.Valid
                    } else {
                        ZipValidation.Invalid(ZipValidation.Reason.WrongStart)
                    }
                }
                val end = path.last()
                if (!isAdjacent(end, move.to, board.n)) {
                    return ZipValidation.Invalid(ZipValidation.Reason.NotAdjacent)
                }
                if (board.blocked(end, move.to)) {
                    return ZipValidation.Invalid(ZipValidation.Reason.WallBetween)
                }
                if (move.to in path) {
                    return ZipValidation.Invalid(ZipValidation.Reason.CellAlreadyInPath)
                }
                val label = board.waypointLabel(move.to)
                if (label != null) {
                    val expectedLabel = path.count(board::isWaypoint) + 1
                    if (label != expectedLabel) {
                        return ZipValidation.Invalid(
                            ZipValidation.Reason.WrongWaypointOrder(board.waypoints[expectedLabel - 1]),
                        )
                    }
                }
                ZipValidation.Valid
            }
        }
    }

    override fun isComplete(state: ZipState): Boolean {
        val board = state.puzzle.board
        val path = state.path
        if (path.isEmpty() || path.size != board.cellCount) return false
        if (path.last() != board.waypoints.last()) return false // I6
        return verifyPath(state.puzzle, path) // I1–I5 + I8
    }

    /** Solver-backed hint (F2 §5 §3): Step onto the next solution cell, or — when the
     *  trace is provably dead — Backtrack to the longest still-solvable prefix. The trim
     *  itself is free; the resulting hint counts once (A3, F2 §10). Reads the solver,
     *  never the stored solution. */
    override fun hint(state: ZipState): Hint = hints.hint(state)

    companion object {
        fun isAdjacent(a: Cell, b: Cell, n: Int): Boolean {
            val diff = b.idx - a.idx
            return (diff == 1 && a.idx / n == b.idx / n) ||
                (diff == -1 && a.idx / n == b.idx / n) ||
                diff == n ||
                diff == -n
        }

        /**
         * Linear verification that [path] satisfies I1–I5 and I8 against [puzzle] — the
         * A16 solution-verification half and the core of [isComplete]. Also implicitly
         * required of any prefix the hint machinery extends.
         */
        fun verifyPath(puzzle: ZipPuzzle, path: List<Cell>): Boolean {
            val board = puzzle.board
            if (path.isEmpty()) return false
            if (path.first() != board.waypoints.first()) return false // I4
            if (path.size > board.cellCount) return false
            val visited = HashSet<Cell>(path.size * 2)
            var waypointsSeen = 0
            for ((index, cell) in path.withIndex()) {
                if (!board.contains(cell)) return false // I8
                if (!visited.add(cell)) return false // I3
                val label = board.waypointLabel(cell)
                if (label != null) {
                    if (label != waypointsSeen + 1) return false // I5
                    waypointsSeen++
                }
                if (index > 0) {
                    val prev = path[index - 1]
                    if (!isAdjacent(prev, cell, board.n)) return false // I1
                    if (board.blocked(prev, cell)) return false // I2
                }
            }
            return true
        }
    }
}
