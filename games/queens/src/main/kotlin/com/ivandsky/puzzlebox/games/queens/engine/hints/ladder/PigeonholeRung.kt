package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

private typealias ConfinementFound = Pair<QueensDeduction.Exclude, () -> Set<Int>>

/**
 * Third rung: k ≥ 2 regions whose open cells lie in k rows (or columns) take those lines'
 * queens, so every other open cell there is excluded. Dually, k rows (or columns) whose
 * open cells lie in k regions take those regions' queens, excluding their cells elsewhere.
 * "k" counts missing queens, so the proof holds for any queens-per-unit.
 *
 * A confinement and its complement prove the same exclusion, so only sets holding at most
 * half the missing queens are tried; k = 1 (and its complement) is line lock's job.
 * The reason names the lowest line (ROW/COLUMN) or region the exclusion lands in.
 * Order: lowest region of an excluded cell, then lowest excluded cell, then ROW → COLUMN → REGION.
 */
object PigeonholeRung : QueensRung {
    override val id: String = "pigeonhole"
    override val level: Difficulty.Level = Difficulty.Level.Hard
    override val captionKey: String = "queens.hint.rung.pigeonhole"

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).sortedWith(order(puzzle)).asSequence().map { (deduction, evidence) -> QueensProof(deduction, evidence()) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).minWithOrNull(order(puzzle))?.first

    private fun order(puzzle: QueensPuzzle): Comparator<ConfinementFound> = compareBy(
        { found -> found.first.cells.minOf(puzzle.regions::get) },
        { it.first.cells.min() },
        { it.first.unit.kind },
    )

    /** Deductions with evidence still unbuilt, so [step] builds none for the losing candidates. */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<ConfinementFound> {
        val n = puzzle.size
        val rows = puzzle.rowCells
        val cols = puzzle.columnCells
        val regions = puzzle.regionCells
        return confined(puzzle, candidates, regions, rows, { it / n }, HintReason.Kind.ROW) +
            confined(puzzle, candidates, regions, cols, { it % n }, HintReason.Kind.COLUMN) +
            confined(puzzle, candidates, rows, regions, puzzle.regions::get, HintReason.Kind.REGION) +
            confined(puzzle, candidates, cols, regions, puzzle.regions::get, HintReason.Kind.REGION)
    }

    /**
     * Every exclusion from a set of [groups] (≥ 2 of them, needing at most half the missing
     * queens) whose open cells fall in [targets] that need exactly as many queens: the other
     * open cells of those targets are excluded. [targetOf] maps a cell to its target index.
     */
    private fun confined(
        puzzle: QueensPuzzle,
        candidates: QueensCandidates,
        groups: List<List<Int>>,
        targets: List<List<Int>>,
        targetOf: (Int) -> Int,
        kind: HintReason.Kind,
    ): List<ConfinementFound> {
        fun missing(unit: List<Int>): Int = puzzle.queensPerUnit - unit.count { it in candidates.queens }
        val open = groups.filter { missing(it) > 0 }
        val total = open.sumOf(::missing)
        val found = ArrayList<ConfinementFound>()
        for (mask in 1 until (1 shl open.size)) {
            if (Integer.bitCount(mask) < 2) continue
            val chosen = open.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            val need = chosen.sumOf(::missing)
            if (need * 2 > total) continue
            val inside = chosen.flatten().toSet()
            val lines = inside.filter(candidates::isOpen).map(targetOf).toSortedSet()
            if (lines.sumOf { missing(targets[it]) } != need) continue
            val cells = lines.flatMap { targets[it] }.filter { candidates.isOpen(it) && it !in inside }.toSet()
            if (cells.isNotEmpty()) {
                val deduction = QueensDeduction.Exclude(this, HintReason(kind, lines.first(), this), cells)
                found += deduction to { chosen.flatten().filter(candidates::isOpen).toSet() }
            }
        }
        return found
    }
}
