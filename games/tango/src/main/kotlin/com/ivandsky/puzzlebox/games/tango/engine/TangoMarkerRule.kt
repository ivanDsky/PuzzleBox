package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * Constraint class 3: `=`/`×` markers bind adjacent cells to same/opposite values (A3, A12).
 * Self-contained unit — [TangoRules] dispatches to it for validation and marker propagation.
 */
object TangoMarkerRule {

    /** First marker edge with both ends filled and the relation broken, or null. */
    fun violation(puzzle: TangoPuzzle, grid: List<TangoCell?>): TangoMarker? =
        puzzle.markers.firstOrNull { marker ->
            val a = grid[marker.cell]
            val b = grid[neighbor(puzzle.n, marker)]
            marker.kind.violates(a, b)
        }

    /** First marker edge with exactly one filled end — the empty end is forced (marker propagation). */
    fun deduction(puzzle: TangoPuzzle, grid: List<TangoCell?>): TangoHint.Forced? =
        forced(puzzle, grid).firstOrNull()

    /** One entry per marker edge with exactly one filled end; a cell on two such edges appears twice. */
    fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoHint.Forced> {
        val found = mutableListOf<TangoHint.Forced>()
        for (marker in puzzle.markers) {
            val nb = neighbor(puzzle.n, marker)
            val a = grid[marker.cell]
            val b = grid[nb]
            if (a != null && b != null) continue
            val filled = a ?: b ?: continue
            val emptyIdx = if (a == null) marker.cell else nb
            val forced = when (marker.kind) {
                TangoMarkerKind.SAME -> filled
                TangoMarkerKind.OPPOSITE -> filled.other
            }
            found += TangoHint.Forced(
                idx = emptyIdx,
                value = forced,
                why = TangoHint.Why.MarkerBinding(marker.cell, nb, marker.kind),
            )
        }
        return found
    }

    /** True iff any marker edge touching [idx] has both ends filled and the relation broken. */
    fun violationAt(puzzle: TangoPuzzle, grid: List<TangoCell?>, idx: Int): Boolean {
        val n = puzzle.n
        return listOf(idx - 1, idx + 1, idx - n, idx + n).any { nb ->
            nb in 0 until puzzle.cellCount &&
                puzzle.markerBetween(idx, nb)?.violates(grid[idx], grid[nb]) == true
        }
    }

    /** The relation `kind` pins between the two cells is broken. */
    fun TangoMarkerKind.violates(a: TangoCell?, b: TangoCell?): Boolean = when (this) {
        TangoMarkerKind.SAME -> a != null && b != null && a != b
        TangoMarkerKind.OPPOSITE -> a != null && b != null && a == b
    }

    private fun neighbor(n: Int, marker: TangoMarker): Int = when (marker.side) {
        com.ivandsky.puzzlebox.games.tango.TangoEdgeSide.RIGHT -> marker.cell + 1
        com.ivandsky.puzzlebox.games.tango.TangoEdgeSide.DOWN -> marker.cell + n
    }
}
