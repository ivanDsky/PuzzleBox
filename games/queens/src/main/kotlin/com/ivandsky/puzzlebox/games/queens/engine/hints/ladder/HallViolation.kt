package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason.Kind

/**
 * Hall's condition between unit kinds: the queens k units of one kind still miss must stand
 * in the units of another kind their open cells reach, so a state where those units have
 * room for fewer queens is unsolvable. A unit with fewer open cells than missing queens
 * (an emptied unit, on one-queen boards) is the k = 1 case. Searches every subset of the
 * ≤ 11 unsolved units of a kind by bitmask, as `lab/engine.js` `violation()` does.
 */
internal object HallViolation {

    /** [units] of [kind] (ascending) whose open cells all lie in [targets] of [targetKind] (ascending). */
    data class Violation(val kind: Kind, val units: List<Int>, val targetKind: Kind, val targets: List<Int>) {
        val k: Int get() = units.size

        /** Every cell of [units], open or not. */
        fun cells(puzzle: QueensPuzzle): List<Int> = puzzle.regions.indices.filter { indexOf(puzzle, kind, it) in units }
    }

    /** The lab's order: it decides which of two equally small violations is reported. */
    private val pairs = listOf(
        Kind.REGION to Kind.ROW, Kind.REGION to Kind.COLUMN, Kind.ROW to Kind.REGION,
        Kind.COLUMN to Kind.REGION, Kind.ROW to Kind.COLUMN, Kind.COLUMN to Kind.ROW,
    )

    /** The violation with the fewest units, the earlier kind pair then the lower subset winning ties; null when none. */
    fun smallest(puzzle: QueensPuzzle, candidates: QueensCandidates): Violation? {
        var best: Violation? = null
        for ((kind, targetKind) in pairs) {
            best = smallest(puzzle, candidates, kind, targetKind, best?.k ?: Int.MAX_VALUE) ?: best
        }
        return best
    }

    /** The smallest violation of [kind] against [targetKind] with fewer than [below] units, or null. */
    private fun smallest(puzzle: QueensPuzzle, candidates: QueensCandidates, kind: Kind, targetKind: Kind, below: Int): Violation? {
        val n = puzzle.size
        val missing = missing(puzzle, candidates, kind)
        val room = missing(puzzle, candidates, targetKind)
        val open = IntArray(n)
        val reach = IntArray(n)
        for (cell in puzzle.regions.indices) {
            if (!candidates.isOpen(cell)) continue
            val unit = indexOf(puzzle, kind, cell)
            open[unit]++
            reach[unit] = reach[unit] or (1 shl indexOf(puzzle, targetKind, cell))
        }
        val groups = (0 until n).filter { missing[it] > 0 }
        val reachOf = IntArray(1 shl groups.size)
        val needOf = IntArray(1 shl groups.size)
        var smallest = below
        var found: Violation? = null
        for (mask in 1 until (1 shl groups.size)) {
            val low = mask and -mask
            val group = groups[Integer.numberOfTrailingZeros(low)]
            reachOf[mask] = reachOf[mask xor low] or reach[group]
            needOf[mask] = needOf[mask xor low] + missing[group]
            val k = Integer.bitCount(mask)
            if (k >= smallest) continue
            val space = (0 until n).sumOf { if (reachOf[mask] shr it and 1 == 1) room[it] else 0 }
            val starved = k == 1 && open[group] < missing[group]
            if (needOf[mask] > space || starved) {
                smallest = k
                found = Violation(
                    kind,
                    groups.filterIndexed { i, _ -> mask shr i and 1 == 1 },
                    targetKind,
                    (0 until n).filter { reachOf[mask] shr it and 1 == 1 },
                )
            }
        }
        return found
    }

    /** Queens each unit of [kind] still misses; 0 for a full (or overfull) unit. */
    private fun missing(puzzle: QueensPuzzle, candidates: QueensCandidates, kind: Kind): IntArray {
        val missing = IntArray(puzzle.size) { puzzle.queensPerUnit }
        for (queen in candidates.queens) missing[indexOf(puzzle, kind, queen)]--
        return IntArray(puzzle.size) { maxOf(0, missing[it]) }
    }

    private fun indexOf(puzzle: QueensPuzzle, kind: Kind, cell: Int): Int = when (kind) {
        Kind.ROW -> cell / puzzle.size
        Kind.COLUMN -> cell % puzzle.size
        Kind.REGION -> puzzle.regions[cell]
    }
}
