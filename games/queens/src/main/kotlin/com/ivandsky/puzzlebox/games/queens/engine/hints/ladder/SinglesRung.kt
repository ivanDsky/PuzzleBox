package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

private typealias PlaceFound = Pair<QueensDeduction.Place, () -> Set<Int>>

/**
 * First rung: a row, column or region whose missing queens fit only its open cells
 * (for N=1: one open cell left) places its lowest open cell.
 *
 * Order, the one today's hints use: lowest region of the placed cell (the region the
 * hint highlights), then lowest cell, then the ROW → COLUMN → REGION proof.
 */
object SinglesRung : QueensRung {
    override val id: String = "singles"
    override val level: Difficulty.Level = Difficulty.Level.Easy
    override val captionKey: String = "queens.hint.rung.singles"

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).sortedWith(order(puzzle)).asSequence().map { (deduction, evidence) -> QueensProof(deduction, evidence()) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).minWithOrNull(order(puzzle))?.first

    private fun order(puzzle: QueensPuzzle): Comparator<PlaceFound> = compareBy(
        { puzzle.regions[it.first.cell] },
        { it.first.cell },
        { it.first.unit.kind },
    )

    /** Deductions with evidence still unbuilt, so [step] builds none for the losing candidates. */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<PlaceFound> {
        val found = ArrayList<PlaceFound>()
        for (kind in HintReason.Kind.entries) {
            val units = QueensUnits.cells(puzzle, kind)
            for (index in units.indices) {
                val cells = units[index]
                val missing = puzzle.queensPerUnit - cells.count { it in candidates.queens }
                val open = cells.filter(candidates::isOpen)
                if (missing > 0 && open.size == missing) found += QueensDeduction.Place(this, HintReason(kind, index), open.first()) to { cells.toSet() }
            }
        }
        return found
    }
}
