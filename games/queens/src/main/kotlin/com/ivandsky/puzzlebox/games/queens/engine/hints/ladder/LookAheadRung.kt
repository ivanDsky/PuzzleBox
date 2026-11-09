package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * Last rung (spec A9): an open cell is excluded when standing a queen on it and running
 * the [propagate] rungs until they stall leaves some row, column or region unable to
 * hold its queens. [propagate] is the lower rungs, passed in so this rung never calls
 * itself.
 *
 * Order: lowest region of the probed cell, then lowest cell. The step names the first
 * unit the probe emptied, ROW → COLUMN → REGION, then lowest unit index.
 */
class LookAheadRung(private val propagate: List<QueensRung>) : QueensRung {
    override val id: String = "look-ahead"
    override val level: Difficulty.Level = Difficulty.Level.Expert
    override val captionKey: String = "queens.hint.rung.look-ahead"

    /** Lazy: probes cells in order and yields at each failed one, so [step] stops at the first. */
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> {
        val units = units(puzzle)
        return puzzle.regions.indices
            .filter(candidates::isOpen)
            .sortedWith(compareBy({ puzzle.regions[it] }, { it }))
            .asSequence()
            .mapNotNull { cell ->
                probe(puzzle, units, candidates.place(puzzle, cell))?.let { (unit, cells) ->
                    QueensProof(QueensDeduction.Exclude(this, unit, setOf(cell)), cells.filter { it != cell && candidates.isOpen(it) }.toSet())
                }
            }
    }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction

    /** Propagates [hypothesis] with the lower rungs; the first emptied unit with its cells, or null if none. */
    private fun probe(
        puzzle: QueensPuzzle,
        units: List<Pair<HintReason, List<Int>>>,
        hypothesis: QueensCandidates,
    ): Pair<HintReason, List<Int>>? {
        var state = hypothesis
        while (true) {
            emptied(puzzle, units, state)?.let { return it }
            state = when (val deduction = propagate.firstNotNullOfOrNull { it.step(puzzle, state) } ?: return null) {
                is QueensDeduction.Place -> state.place(puzzle, deduction.cell)
                is QueensDeduction.Exclude -> state.exclude(deduction.cells)
            }
        }
    }

    private fun emptied(puzzle: QueensPuzzle, units: List<Pair<HintReason, List<Int>>>, state: QueensCandidates): Pair<HintReason, List<Int>>? =
        units.firstOrNull { (_, cells) ->
            cells.count { it in state.queens } + cells.count(state::isOpen) < puzzle.queensPerUnit
        }

    private fun units(puzzle: QueensPuzzle): List<Pair<HintReason, List<Int>>> {
        val n = puzzle.size
        val byRegion = puzzle.regions.indices.groupBy { puzzle.regions[it] }
        return List(n) { row -> HintReason(HintReason.Kind.ROW, row, this) to List(n) { row * n + it } } +
            List(n) { col -> HintReason(HintReason.Kind.COLUMN, col, this) to List(n) { it * n + col } } +
            List(n) { region -> HintReason(HintReason.Kind.REGION, region, this) to byRegion[region].orEmpty() }
    }
}
