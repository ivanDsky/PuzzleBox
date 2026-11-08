package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move

/**
 * The rules state: puzzle plus the full grid (givens + player placements). Pure, JVM-only.
 */
data class TangoState(
    val puzzle: TangoPuzzle,
    /** Row-major, `null` = empty player cell; givens always present. */
    val grid: List<TangoCell?>,
)

/**
 * Tango rules — exactly the three constraint classes at any size (A3). [TangoRules] is the
 * dispatcher; each class is a self-contained unit ([TangoMarkerRule], [TangoTripleRule],
 * [TangoCountRule]); [TangoLineRule] combines them inside one line for [hint]. Move-time
 * verdicts name the violated rule (spec §2); [isComplete] requires every cell filled and
 * every constraint satisfied. [hint] walks the forced-move ladder (spec hints bullet): marker
 * propagation → three-in-a-row → count-completion → line completion, each hint revealing WHY.
 * Uniqueness (A15) is [TangoUniqueness.isUniquelySolvable], beside the rules as a solver.
 */
object TangoRules : GameRules<TangoState> {

    override fun validate(move: Move, state: TangoState): TangoVerdict {
        // Safe-cast: validation is total over every Move, never throws (contract note).
        if (move !is TangoMove) return TangoVerdict.Rejected(TangoVerdict.Reason.ForeignMove)
        val (idx, value) = move
        if (idx !in 0 until state.puzzle.cellCount) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.ForeignMove)
        }
        if (state.puzzle.isGiven(idx)) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.LockedGiven, idx)
        }
        if (value == null || state.grid[idx] == value) return TangoVerdict.Valid

        val candidate = state.grid.toMutableList().also { it[idx] = value }

        // Dispatch in class order: marker edges around the placed cell, then its row/column.
        if (TangoMarkerRule.violationAt(state.puzzle, candidate, idx)) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.MarkerConflict, idx)
        }
        if (TangoTripleRule.violation(state.puzzle, candidate)?.around(idx, state.puzzle.n) == true) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.ThreeInARow, idx)
        }
        if (TangoCountRule.violation(state.puzzle, candidate) != null) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.CountExceeded, idx)
        }
        return TangoVerdict.Valid
    }

    override fun isComplete(state: TangoState): Boolean {
        if (state.grid.any { it == null }) return false
        return TangoMarkerRule.violation(state.puzzle, state.grid) == null &&
            TangoTripleRule.violation(state.puzzle, state.grid) == null &&
            TangoCountRule.violation(state.puzzle, state.grid) == null
    }

    /** The forced-move ladder: first deduction wins; none found → [TangoHint.Stuck]. */
    override fun hint(state: TangoState): TangoHint =
        TangoMarkerRule.deduction(state.puzzle, state.grid)
            ?: TangoTripleRule.deduction(state.puzzle, state.grid)
            ?: TangoCountRule.deduction(state.puzzle, state.grid)
            ?: TangoLineRule.deduction(state.puzzle, state.grid)
            ?: TangoHint.Stuck
}

/** True iff [idx] lies in the row or column of the reported violation cell. */
private fun Int.around(idx: Int, n: Int): Boolean {
    val r = this / n
    val c = this % n
    return r == idx / n || c == idx % n
}
