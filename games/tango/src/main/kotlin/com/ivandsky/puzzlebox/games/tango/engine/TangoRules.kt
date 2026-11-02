package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint

/**
 * The rules state: puzzle plus the full grid (givens + player placements). Pure, JVM-only.
 */
data class TangoState(
    val puzzle: TangoPuzzle,
    /** Row-major, `null` = empty player cell; givens always present. */
    val grid: List<TangoCell?>,
)

/**
 * Tango rules — exactly the three constraint classes at any size (A3):
 * 1. no three identical cells in any row or column;
 * 2. equal suns and moons per row and column (n/2 each);
 * 3. `=`/`×` markers bind adjacent cells to same/opposite values.
 *
 * Move-time verdicts name the violated rule (spec §2); [isComplete] requires every cell
 * filled and every constraint satisfied. Uniqueness checking (A15) lives beside the rules
 * as a solver, not on this interface. The hint ladder is ticket-02 scope; the stub keeps
 * the [GameRules] contract total without building it here.
 */
object TangoRules : GameRules<TangoState> {

    override fun validate(move: com.ivandsky.puzzlebox.game.Move, state: TangoState): TangoVerdict {
        // Safe-cast: validation is total over every Move, never throws (contract note).
        if (move !is TangoMove) return TangoVerdict.Rejected(TangoVerdict.Reason.ForeignMove)
        val (idx, value) = move
        if (idx !in 0 until state.puzzle.cellCount) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.ForeignMove)
        }
        if (state.puzzle.isGiven(idx)) return TangoVerdict.Rejected(TangoVerdict.Reason.LockedGiven, idx)
        if (value == null || state.grid[idx] == value) return TangoVerdict.Valid

        val n = state.puzzle.n
        val row = idx / n
        val col = idx % n
        val candidate = state.grid.toMutableList().also { it[idx] = value }

        // Marker violations involve only the placed cell — check its marker edges first.
        val markerConflict = listOf(idx - 1, idx + 1, idx - n, idx + n).any { neighbor ->
            neighbor in 0 until state.puzzle.cellCount &&
                state.puzzle.markerBetween(idx, neighbor)?.violates(candidate[idx], candidate[neighbor]) == true
        }
        if (markerConflict) return TangoVerdict.Rejected(TangoVerdict.Reason.MarkerConflict, idx)

        if (hasTriple(candidate, n, row, col)) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.ThreeInARow, idx)
        }
        if (halfExceeded(candidate, n, row, col)) {
            return TangoVerdict.Rejected(TangoVerdict.Reason.CountExceeded, idx)
        }
        return TangoVerdict.Valid
    }

    override fun isComplete(state: TangoState): Boolean {
        val n = state.puzzle.n
        val grid = state.grid
        if (grid.any { it == null }) return false
        for (i in 0 until n) {
            val row = (0 until n).map { grid[i * n + it] }
            val col = (0 until n).map { grid[it * n + i] }
            if (row.count { it == TangoCell.SUN } != n / 2) return false
            if (col.count { it == TangoCell.SUN } != n / 2) return false
        }
        for (i in 0 until n) {
            if (hasTriple(grid, n, rowIndex = i, colIndex = null)) return false
            if (hasTriple(grid, n, rowIndex = null, colIndex = i)) return false
        }
        return state.puzzle.markers.all { marker ->
            val a = marker.cell
            val b = when (marker.side) {
                com.ivandsky.puzzlebox.games.tango.TangoEdgeSide.RIGHT -> a + 1
                com.ivandsky.puzzlebox.games.tango.TangoEdgeSide.DOWN -> a + n
            }
            !marker.kind.violates(grid[a], grid[b])
        }
    }

    override fun hint(state: TangoState): Hint = TangoHint.NotYet

    // -- helpers --------------------------------------------------------------

    private fun TangoMarkerKind.violates(a: TangoCell?, b: TangoCell?): Boolean = when (this) {
        TangoMarkerKind.SAME -> a != null && b != null && a != b
        TangoMarkerKind.OPPOSITE -> a != null && b != null && a == b
    }

    /** Any run of three identical cells within [rowIndex]'s row or [colIndex]'s column. */
    private fun hasTriple(grid: List<TangoCell?>, n: Int, rowIndex: Int?, colIndex: Int?): Boolean {
        if (rowIndex != null) {
            var run = 1
            for (c in 1 until n) {
                val prev = grid[rowIndex * n + c - 1]
                val cur = grid[rowIndex * n + c]
                run = if (prev != null && prev == cur) run + 1 else 1
                if (run >= 3) return true
            }
        }
        if (colIndex != null) {
            var run = 1
            for (r in 1 until n) {
                val prev = grid[(r - 1) * n + colIndex]
                val cur = grid[r * n + colIndex]
                run = if (prev != null && prev == cur) run + 1 else 1
                if (run >= 3) return true
            }
        }
        return false
    }

    /** Would the row/column through the placed cell exceed n/2 of one value? */
    private fun halfExceeded(grid: List<TangoCell?>, n: Int, row: Int, col: Int): Boolean {
        val half = n / 2
        val rowLine = (0 until n).map { grid[row * n + it] }
        val colLine = (0 until n).map { grid[it * n + col] }
        return rowLine.count { it == TangoCell.SUN } > half || rowLine.count { it == TangoCell.MOON } > half ||
            colLine.count { it == TangoCell.SUN } > half || colLine.count { it == TangoCell.MOON } > half
    }
}
