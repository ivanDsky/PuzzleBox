package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoLines

/**
 * D5 sign count: a `×` joining two empty cells of a line holds one sun and one moon, so the line
 * counts it as one of each. Once a value is used up, every other empty cell takes the other value.
 * An `=` joining two other empty cells needs two of one value, so with one left both take the other.
 */
object TangoSignCountRule : TangoStepRule {

    /** Rows first then columns; a target set already emitted by an earlier line or pairing is not repeated. */
    override fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction> {
        val n = puzzle.n
        val found = mutableListOf<TangoStep.Deduction>()
        val seen = mutableSetOf<Map<Int, TangoCell>>()
        for (line in 0 until 2 * n) {
            val row = line.takeIf { it < n }
            val col = (line - n).takeIf { it >= 0 }
            fun emit(targets: Map<Int, TangoCell>, evidence: Set<Int>) {
                if (targets.isEmpty() || !seen.add(targets)) return
                for ((idx, value) in targets) {
                    found += TangoStep.Deduction(TangoStepRung.SIGN_COUNT, idx, value, evidence, row, col, options = null)
                }
            }
            var suns = 0
            var moons = 0
            for (j in 0 until n) when (grid[TangoLines.cellAt(n, line, j)]) {
                TangoCell.SUN -> suns++
                TangoCell.MOON -> moons++
                null -> Unit
            }
            for (pairs in oppositePairings(puzzle, grid, line)) {
                val sunsLeft = n / 2 - suns - pairs.size
                val moonsLeft = n / 2 - moons - pairs.size
                // No over-full guard: single cells number sunsLeft + moonsLeft, so with either one
                // negative no rest cell and no `=` pair of singles is left to emit.
                fun left(value: TangoCell) = if (value == TangoCell.SUN) sunsLeft else moonsLeft
                val inPair = BooleanArray(n)
                val pairCells = LinkedHashSet<Int>()
                for (at in pairs) {
                    inPair[at] = true
                    inPair[at + 1] = true
                    pairCells += TangoLines.cellAt(n, line, at)
                    pairCells += TangoLines.cellAt(n, line, at + 1)
                }
                val single = BooleanArray(n) { j -> grid[TangoLines.cellAt(n, line, j)] == null && !inPair[j] }
                // With no pair counted a used-up value is Full line (D3), not a sign count.
                if (pairs.isNotEmpty()) for (value in TangoCell.entries) if (left(value) == 0) {
                    val rest = LinkedHashMap<Int, TangoCell>()
                    for (j in 0 until n) if (single[j]) rest[TangoLines.cellAt(n, line, j)] = value.other
                    emit(rest, pairCells)
                }
                for (j in 0 until n - 1) {
                    val a = TangoLines.cellAt(n, line, j)
                    val b = TangoLines.cellAt(n, line, j + 1)
                    if (!single[j] || !single[j + 1] || puzzle.markerBetween(a, b) != TangoMarkerKind.SAME) continue
                    for (value in TangoCell.entries) if (left(value) == 1) emit(mapOf(a to value.other, b to value.other), pairCells + setOf(a, b))
                }
            }
        }
        return found
    }

    /**
     * Greedy disjoint `×` pairs of empty cells scanned from each end of the line, as line positions
     * of each pair's first cell; two touching `×` markers pair differently from each end, and each
     * pairing is a sound count on its own.
     */
    private fun oppositePairings(puzzle: TangoPuzzle, grid: List<TangoCell?>, line: Int): List<List<Int>> {
        val n = puzzle.n
        return listOf(0 until n - 1, (0 until n - 1).reversed()).map { order ->
            val used = BooleanArray(n)
            val starts = mutableListOf<Int>()
            for (j in order) {
                val a = TangoLines.cellAt(n, line, j)
                val b = TangoLines.cellAt(n, line, j + 1)
                if (grid[a] != null || grid[b] != null || used[j] || used[j + 1]) continue
                if (puzzle.markerBetween(a, b) != TangoMarkerKind.OPPOSITE) continue
                starts += j
                used[j] = true
                used[j + 1] = true
            }
            starts.sorted()
        }.distinct()
    }
}
