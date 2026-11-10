package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

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
        found(puzzle, candidates).proofs(order(puzzle))

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).first(order(puzzle))

    private fun order(puzzle: QueensPuzzle): Comparator<Found<QueensDeduction.Place>> = compareBy(
        { puzzle.regions[it.deduction.cell] },
        { it.deduction.cell },
        { it.deduction.unit.kind },
    )

    /**
     * The cells [steps] would place, in its order, each once: no [QueensProof] and no evidence is built.
     * The chain hot loop reads only these. A cell two units name stands once; sorting by
     * (region, cell) is [order] with the kind tie-break gone, since one cell has one region.
     */
    fun cells(puzzle: QueensPuzzle, candidates: QueensCandidates): List<Int> {
        val total = puzzle.regions.size
        var keys = NO_KEYS
        var count = 0
        forEachSingle(puzzle, candidates) { _, _, cell ->
            if (count == keys.size) keys = keys.copyOf(maxOf(8, count * 2))
            keys[count++] = puzzle.regions[cell] * total + cell
        }
        if (count == 0) return emptyList()
        keys.sort(0, count)
        val cells = ArrayList<Int>(count)
        for (i in 0 until count) if (i == 0 || keys[i] != keys[i - 1]) cells += keys[i] % total
        return cells
    }

    /** Deductions with evidence still unbuilt, so [step] builds none for the losing candidates. */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<Found<QueensDeduction.Place>> {
        val found = ArrayList<Found<QueensDeduction.Place>>()
        forEachSingle(puzzle, candidates) { kind, index, cell ->
            found += Found(QueensDeduction.Place(HintReason(kind, index), cell)) { QueensUnits.cells(puzzle, kind)[index].toSet() }
        }
        return found
    }

    /** [action] for each ROW, COLUMN, REGION unit (in that order) whose missing queens fit its open cells: its kind, index and lowest open cell. */
    private inline fun forEachSingle(puzzle: QueensPuzzle, candidates: QueensCandidates, action: (HintReason.Kind, Int, Int) -> Unit) {
        for (kind in HintReason.Kind.entries) {
            val units = QueensUnits.cells(puzzle, kind)
            for (index in units.indices) {
                var queens = 0
                var open = 0
                var lowest = -1
                for (cell in units[index]) {
                    when {
                        cell in candidates.queens -> queens++
                        cell !in candidates.excluded -> {
                            if (open++ == 0) lowest = cell
                        }
                    }
                }
                val missing = puzzle.queensPerUnit - queens
                if (missing > 0 && open == missing) action(kind, index, lowest)
            }
        }
    }

    private val NO_KEYS = IntArray(0)
}
