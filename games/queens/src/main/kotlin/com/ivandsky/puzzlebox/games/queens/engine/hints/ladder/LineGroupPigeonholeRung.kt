package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D6, the row↔column case [PigeonholeRung] leaves out (X-wing shape): k ≥ 2 rows whose open
 * cells fall in columns needing exactly the rows' missing queens take those columns' queens,
 * so every other open cell of those columns is excluded. The same with columns confined to
 * rows. "Missing queens" is what is counted, so the proof holds for any queens-per-unit.
 *
 * Rows R confined to columns T is the same proof as the other columns confined to the other
 * rows, so only sets holding at most half the missing queens are tried. A set with a confined
 * proper subset is skipped: that subset is the smaller proof. The reason names the lowest
 * target line. Order: smallest k, then lowest excluded cell, then lowest unit.
 */
object LineGroupPigeonholeRung : QueensRung {
    override val id: String = "line-group-pigeonhole"
    override val level: Difficulty.Level = Difficulty.Level.Hard
    override val captionKey: String = "queens.hint.rung.line-group-pigeonhole"

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).sortedWith(order).asSequence().map { it.proof }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).minWithOrNull(order)?.proof?.deduction

    private class Found(val k: Int, val proof: QueensProof) {
        val cells: Set<Int> = (proof.deduction as QueensDeduction.Exclude).cells
    }

    private val order: Comparator<Found> = compareBy(
        { it.k },
        { it.cells.min() },
        { it.proof.deduction.unit.kind },
        { it.proof.deduction.unit.unit },
    )

    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<Found> {
        val n = puzzle.size
        val rows = List(n) { row -> List(n) { row * n + it } }
        val cols = List(n) { col -> List(n) { it * n + col } }
        return confined(puzzle, candidates, rows, cols, { it % n }, HintReason.Kind.COLUMN) +
            confined(puzzle, candidates, cols, rows, { it / n }, HintReason.Kind.ROW)
    }

    /**
     * Every exclusion from a set of [groups] lines confined to [targets] lines; [targetOf] maps a
     * cell to its target index and [kind] is the targets' unit kind. Masks index the unsolved groups.
     */
    private fun confined(
        puzzle: QueensPuzzle,
        candidates: QueensCandidates,
        groups: List<List<Int>>,
        targets: List<List<Int>>,
        targetOf: (Int) -> Int,
        kind: HintReason.Kind,
    ): List<Found> {
        fun missing(unit: List<Int>): Int = puzzle.queensPerUnit - unit.count { it in candidates.queens }
        val unsolved = groups.filter { missing(it) > 0 }
        val opens = unsolved.map { it.filter(candidates::isOpen) }
        val targetMissing = targets.map(::missing)
        val total = unsolved.sumOf(::missing)
        val subsets = 1 shl unsolved.size

        // Per mask: missing queens of the chosen groups, bitmask of the targets their open cells reach,
        // and whether those targets need exactly that many (a group with no open cell never confines).
        val need = IntArray(subsets)
        val reach = IntArray(subsets)
        val dead = BooleanArray(subsets)
        val isConfined = BooleanArray(subsets)
        for (mask in 1 until subsets) {
            val low = Integer.numberOfTrailingZeros(mask)
            val rest = mask and (mask - 1)
            need[mask] = need[rest] + missing(unsolved[low])
            reach[mask] = reach[rest] or opens[low].fold(0) { bits, cell -> bits or (1 shl targetOf(cell)) }
            dead[mask] = dead[rest] || opens[low].isEmpty()
            isConfined[mask] = !dead[mask] && targetMissing.indices.sumOf { if (reach[mask] and (1 shl it) != 0) targetMissing[it] else 0 } == need[mask]
        }

        val found = ArrayList<Found>()
        for (mask in 1 until subsets) {
            if (Integer.bitCount(mask) < 2 || need[mask] * 2 > total || !isConfined[mask]) continue
            if (hasConfinedSubset(mask, isConfined)) continue
            val chosen = unsolved.indices.filter { mask and (1 shl it) != 0 }
            val inside = chosen.flatMap { unsolved[it] }.toSet()
            val lines = targets.indices.filter { reach[mask] and (1 shl it) != 0 }
            val cells = lines.flatMap { targets[it] }.filter { candidates.isOpen(it) && it !in inside }.toSet()
            if (cells.isEmpty()) continue
            val deduction = QueensDeduction.Exclude(this, HintReason(kind, lines.first(), this), cells)
            found += Found(chosen.size, QueensProof(deduction, chosen.flatMap { opens[it] }.toSet()))
        }
        return found
    }

    private fun hasConfinedSubset(mask: Int, isConfined: BooleanArray): Boolean {
        var subset = (mask - 1) and mask
        while (subset > 0) {
            if (isConfined[subset]) return true
            subset = (subset - 1) and mask
        }
        return false
    }
}
