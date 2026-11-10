package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoLines

/**
 * D7 count squeeze: suppose a value on one empty cell of a line. If that uses up a value's count,
 * the rest of the line is forced to the other one; if that fill breaks the line (three in a row or
 * a marker), the cell holds the other value. Evidence is the what-if fill, without the cell itself.
 */
object TangoCountSqueezeRule : TangoStepRule {

    /** Rows first then columns, cells in line order, the sun what-if before the moon one. */
    override fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction> {
        val n = puzzle.n
        val found = mutableListOf<TangoStep.Deduction>()
        val lines = TangoLines.all(n)
        val vals = arrayOfNulls<TangoCell>(n)
        for (line in 0 until 2 * n) {
            val cells = lines[line]
            for (j in 0 until n) {
                if (grid[cells[j]] != null) continue
                for (value in TangoCell.entries) {
                    for (q in 0 until n) vals[q] = grid[cells[q]]
                    vals[j] = value
                    val suns = vals.count { it == TangoCell.SUN }
                    val moons = vals.count { it == TangoCell.MOON }
                    // Over the count is a plain count break: Full line (D3) owns it.
                    if (suns > n / 2 || moons > n / 2) continue
                    val fill = when (n / 2) {
                        suns -> TangoCell.MOON
                        moons -> TangoCell.SUN
                        else -> continue
                    }
                    for (q in 0 until n) if (vals[q] == null) vals[q] = fill
                    if (!breaks(puzzle, cells, vals)) continue
                    val filled = LinkedHashSet<Int>()
                    for (q in 0 until n) if (q != j && grid[cells[q]] == null) filled += cells[q]
                    found += TangoStep.Deduction(
                        TangoStepRung.COUNT_SQUEEZE, cells[j], value.other, filled,
                        row = line.takeIf { it < n },
                        col = (line - n).takeIf { it >= 0 },
                        options = null,
                    )
                }
            }
        }
        return found
    }

    /** Whether the full line [vals] at [cells] has three equal in a row or a broken marker. */
    private fun breaks(puzzle: TangoPuzzle, cells: IntArray, vals: Array<TangoCell?>): Boolean {
        for (q in 0 until vals.size - 2) if (vals[q] == vals[q + 1] && vals[q] == vals[q + 2]) return true
        for (q in 0 until vals.size - 1) {
            val broken = when (puzzle.markerBetween(cells[q], cells[q + 1])) {
                TangoMarkerKind.SAME -> vals[q] != vals[q + 1]
                TangoMarkerKind.OPPOSITE -> vals[q] == vals[q + 1]
                null -> false
            }
            if (broken) return true
        }
        return false
    }
}
