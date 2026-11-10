package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoLines

/**
 * D6 split pair: two empty cells between a sun and a moon (`sun _ _ moon`) hold one of each, since
 * equal cells there make three equal next to one end. Counted like `×` pairs, once a value is used
 * up in the line every other empty cell of it takes the other value.
 */
object TangoSplitPairRule : TangoStepRule {

    /** Two adjacent empty cells at line positions [at], [at]+1 holding one sun and one moon; [ends] flank a split, null for a `×` pair. */
    private class OneOfEach(val at: Int, val ends: List<Int>?)

    /** Rows first then columns; a target set already emitted by an earlier line or scan is not repeated. */
    override fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction> {
        val n = puzzle.n
        val found = mutableListOf<TangoStep.Deduction>()
        val seen = mutableSetOf<Map<Int, TangoCell>>()
        for (line in 0 until 2 * n) {
            var suns = 0
            var moons = 0
            for (j in 0 until n) when (grid[TangoLines.cellAt(n, line, j)]) {
                TangoCell.SUN -> suns++
                TangoCell.MOON -> moons++
                null -> Unit
            }
            for (pairs in pairings(puzzle, grid, line)) {
                if (pairs.none { it.ends != null }) continue
                // No over-full guard: filled and paired cells share the line's n cells, so a value
                // can only overflow once no rest cell is left.
                val usedUp = when (n / 2 - pairs.size) {
                    suns -> TangoCell.SUN
                    moons -> TangoCell.MOON
                    else -> continue
                }
                val inPair = BooleanArray(n)
                val evidence = LinkedHashSet<Int>()
                for (pair in pairs) {
                    inPair[pair.at] = true
                    inPair[pair.at + 1] = true
                    evidence += TangoLines.cellAt(n, line, pair.at)
                    evidence += TangoLines.cellAt(n, line, pair.at + 1)
                    pair.ends?.let { evidence += it }
                }
                val targets = LinkedHashMap<Int, TangoCell>()
                for (j in 0 until n) {
                    val idx = TangoLines.cellAt(n, line, j)
                    if (grid[idx] == null && !inPair[j]) targets[idx] = usedUp.other
                }
                if (targets.isEmpty() || !seen.add(targets)) continue
                for ((idx, value) in targets) {
                    found += TangoStep.Deduction(
                        TangoStepRung.SPLIT_PAIR, idx, value, evidence,
                        row = line.takeIf { it < n },
                        col = (line - n).takeIf { it >= 0 },
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
    private fun pairings(puzzle: TangoPuzzle, grid: List<TangoCell?>, line: Int): List<List<OneOfEach>> {
        val n = puzzle.n
        return listOf(0 until n - 1, (0 until n - 1).reversed()).map { order ->
            val used = BooleanArray(n)
            val pairs = mutableListOf<OneOfEach>()
            for (j in order) {
                val a = TangoLines.cellAt(n, line, j)
                val b = TangoLines.cellAt(n, line, j + 1)
                if (grid[a] != null || grid[b] != null || used[j] || used[j + 1]) continue
                val before = if (j > 0) TangoLines.cellAt(n, line, j - 1) else -1
                val after = if (j + 2 < n) TangoLines.cellAt(n, line, j + 2) else -1
                val pair = when {
                    puzzle.markerBetween(a, b) == TangoMarkerKind.OPPOSITE -> OneOfEach(j, ends = null)
                    before >= 0 && after >= 0 && grid[before] != null && grid[after] != null && grid[before] != grid[after] ->
                        OneOfEach(j, ends = listOf(before, after))
                    else -> continue
                }
                pairs += pair
                used[j] = true
                used[j + 1] = true
            }
            pairs
        }
    }
}
