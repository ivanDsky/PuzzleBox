package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D9: an open cell is excluded when a queen on it would leave k ≥ 2 units of one kind
 * whose open cells reach units of another kind with room for fewer queens than they miss
 * ([HallViolation]). k = 1, a unit left no room, is [SqueezeRung]'s. One exclusion per
 * distinct violation, listing every cell that causes it and naming the violation's lowest
 * unit; the evidence is the open cells of the violation's units.
 *
 * Order: the named unit (ROW → COLUMN → REGION, then index), then lowest excluded cell.
 */
object CountSqueezeRung : QueensRung {
    override val id: String = "count-squeeze"
    override val level: Difficulty.Level = Difficulty.Level.Hard
    override val captionKey: String = "queens.hint.rung.count-squeeze"

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).proofs(order)

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).first(order)

    private val order: Comparator<Found<QueensDeduction.Exclude>> =
        compareBy({ it.deduction.unit.kind }, { it.deduction.unit.unit }, { it.deduction.cells.min() })

    /** Eager: the order needs every violation, so every open cell is probed. */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<Found<QueensDeduction.Exclude>> {
        val causes = LinkedHashMap<HallViolation.Violation, MutableSet<Int>>()
        val scratch = HallViolation.Scratch(puzzle)
        for (x in puzzle.regions.indices) {
            if (!candidates.isOpen(x)) continue
            val violation = HallViolation.smallest(candidates.place(puzzle, x), scratch)?.takeIf { it.k >= 2 } ?: continue
            causes.getOrPut(violation, ::mutableSetOf) += x
        }
        return causes.map { (violation, cells) ->
            val unit = HintReason(violation.kind, violation.units.first(), this)
            Found(QueensDeduction.Exclude(this, unit, cells)) {
                violation.cells(puzzle).filter { candidates.isOpen(it) && it !in cells }.toSet()
            }
        }
    }
}
