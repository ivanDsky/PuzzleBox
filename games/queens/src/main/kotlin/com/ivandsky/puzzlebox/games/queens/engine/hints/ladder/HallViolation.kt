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
        fun cells(puzzle: QueensPuzzle): List<Int> = puzzle.regions.indices.filter { QueensUnits.indexOf(puzzle, kind, it) in units }
    }

    /** The lab's order: it decides which of two equally small violations is reported. */
    private val pairs = listOf(
        Kind.REGION to Kind.ROW, Kind.REGION to Kind.COLUMN, Kind.ROW to Kind.REGION,
        Kind.COLUMN to Kind.REGION, Kind.ROW to Kind.COLUMN, Kind.COLUMN to Kind.ROW,
    )

    /** The violation with the fewest units, the earlier kind pair then the lower subset winning ties; null when none. */
    fun smallest(puzzle: QueensPuzzle, candidates: QueensCandidates): Violation? = smallest(candidates, Scratch(puzzle))

    /** [smallest] reusing [scratch]'s buffers, for callers that probe many states of one puzzle. */
    fun smallest(candidates: QueensCandidates, scratch: Scratch): Violation? {
        scratch.load(candidates)
        var best: Violation? = null
        for ((kind, targetKind) in pairs) {
            best = scratch.smallest(kind, targetKind, best?.k ?: Int.MAX_VALUE) ?: best
        }
        return best
    }

    /**
     * Buffers for one puzzle, refilled per state: D9 probes ~100 states per call, and fresh
     * subset arrays (2 × 2¹¹ ints on 11×11) per probe dominate its cost.
     */
    class Scratch(private val puzzle: QueensPuzzle) {
        private val n = puzzle.size
        private val unitOf = Array(Kind.entries.size) { kind -> IntArray(n * n) { QueensUnits.indexOf(puzzle, Kind.entries[kind], it) } }
        private val isOpen = BooleanArray(n * n)
        private val missingOf = Array(Kind.entries.size) { IntArray(n) }
        private val open = IntArray(n)
        private val reach = IntArray(n)
        private val groups = IntArray(n)
        private val reachOf = IntArray(1 shl n)
        private val needOf = IntArray(1 shl n)

        /** Reads [candidates]' open cells and each unit's missing queens (0 for a full or overfull unit). */
        internal fun load(candidates: QueensCandidates) {
            for (cell in 0 until n * n) isOpen[cell] = candidates.isOpen(cell)
            for (kind in missingOf.indices) {
                val left = missingOf[kind]
                left.fill(puzzle.queensPerUnit)
                for (queen in candidates.queens) left[unitOf[kind][queen]]--
                for (unit in 0 until n) left[unit] = maxOf(0, left[unit])
            }
        }

        /** The smallest violation of [kind] against [targetKind] with fewer than [below] units, or null. */
        internal fun smallest(kind: Kind, targetKind: Kind, below: Int): Violation? {
            val missing = missingOf[kind.ordinal]
            val room = missingOf[targetKind.ordinal]
            val unitOfKind = unitOf[kind.ordinal]
            val unitOfTarget = unitOf[targetKind.ordinal]
            open.fill(0)
            reach.fill(0)
            for (cell in 0 until n * n) {
                if (!isOpen[cell]) continue
                val unit = unitOfKind[cell]
                open[unit]++
                reach[unit] = reach[unit] or (1 shl unitOfTarget[cell])
            }
            var m = 0
            for (unit in 0 until n) if (missing[unit] > 0) groups[m++] = unit
            var smallest = below
            var found: Violation? = null
            for (mask in 1 until (1 shl m)) {
                val low = mask and -mask
                val group = groups[Integer.numberOfTrailingZeros(low)]
                reachOf[mask] = reachOf[mask xor low] or reach[group]
                needOf[mask] = needOf[mask xor low] + missing[group]
                val k = Integer.bitCount(mask)
                if (k >= smallest) continue
                var space = 0
                for (target in 0 until n) if (reachOf[mask] shr target and 1 == 1) space += room[target]
                val starved = k == 1 && open[group] < missing[group]
                if (needOf[mask] > space || starved) {
                    smallest = k
                    found = Violation(
                        kind,
                        (0 until m).filter { mask shr it and 1 == 1 }.map { groups[it] },
                        targetKind,
                        (0 until n).filter { reachOf[mask] shr it and 1 == 1 },
                    )
                }
            }
            return found
        }
    }
}
