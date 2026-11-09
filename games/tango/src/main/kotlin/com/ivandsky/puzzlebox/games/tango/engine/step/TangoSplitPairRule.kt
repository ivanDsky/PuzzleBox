package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * D6 split pair: two empty cells between a sun and a moon (`sun _ _ moon`) hold one of each, since
 * equal cells there make three equal next to one end. Counted like `×` pairs, once a value is used
 * up in the line every other empty cell of it takes the other value.
 */
object TangoSplitPairRule : TangoStepRule {

    /** Two adjacent empty cells known to hold one sun and one moon; [ends] are the split's flanks, null for a `×` pair. */
    private class OneOfEach(val at: Int, val ends: List<Int>?)

    /** Rows first then columns; a target set already emitted by an earlier line or scan is not repeated. */
    override fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction> {
        val half = puzzle.n / 2
        val found = mutableListOf<TangoStep.Deduction>()
        val seen = mutableSetOf<Map<Int, TangoCell>>()
        for ((index, line) in lines(puzzle.n).withIndex()) {
            val isRow = index < puzzle.n
            for (pairs in pairings(puzzle, grid, line)) {
                if (pairs.none { it.ends != null }) continue
                val inPair = pairs.flatMap { listOf(line[it.at], line[it.at + 1]) }.toSet()
                val rest = line.filter { grid[it] == null && it !in inPair }
                if (rest.isEmpty()) continue
                val need = TangoCell.entries.associateWith { v -> half - line.count { grid[it] == v } - pairs.size }
                if (need.values.any { it < 0 }) continue
                val usedUp = need.entries.firstOrNull { it.value == 0 }?.key ?: continue
                val targets = rest.associateWith { usedUp.other() }
                if (!seen.add(targets)) continue
                val evidence = pairs.flatMap { listOf(line[it.at], line[it.at + 1]) + it.ends.orEmpty() }.toSet()
                targets.forEach { (idx, value) ->
                    found += TangoStep.Deduction(
                        TangoStepRung.SPLIT_PAIR, idx, value, evidence,
                        row = index.takeIf { isRow },
                        col = (index - puzzle.n).takeIf { !isRow },
                        options = null,
                    )
                }
            }
        }
        return found
    }

    /**
     * Greedy disjoint pairings scanned from each end of the line; the two scans can pair a run of
     * empty cells differently, and each pairing is a sound count on its own.
     */
    private fun pairings(puzzle: TangoPuzzle, grid: List<TangoCell?>, line: List<Int>): List<List<OneOfEach>> {
        val starts = (0 until line.size - 1).toList()
        return listOf(starts, starts.reversed()).map { order ->
            val used = BooleanArray(line.size)
            val pairs = mutableListOf<OneOfEach>()
            for (j in order) {
                val a = line[j]
                val b = line[j + 1]
                if (grid[a] != null || grid[b] != null || used[j] || used[j + 1]) continue
                val before = line.getOrNull(j - 1)?.let { grid[it] }
                val after = line.getOrNull(j + 2)?.let { grid[it] }
                val pair = when {
                    puzzle.markerBetween(a, b) == TangoMarkerKind.OPPOSITE -> OneOfEach(j, ends = null)
                    before != null && after != null && before != after -> OneOfEach(j, ends = listOf(line[j - 1], line[j + 2]))
                    else -> continue
                }
                pairs += pair
                used[j] = true
                used[j + 1] = true
            }
            pairs
        }
    }

    /** Every row then every column of an n×n board, as row-major cell indices. */
    private fun lines(n: Int): List<List<Int>> =
        (0 until n).map { r -> (0 until n).map { c -> r * n + c } } + (0 until n).map { c -> (0 until n).map { r -> r * n + c } }

    private fun TangoCell.other(): TangoCell = if (this == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
