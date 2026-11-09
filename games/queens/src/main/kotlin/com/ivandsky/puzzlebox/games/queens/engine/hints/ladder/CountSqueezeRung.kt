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

    /** Eager: the order needs every violation, so [step] probes every open cell too. */
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> {
        val causes = LinkedHashMap<HallViolation.Violation, MutableSet<Int>>()
        for (x in puzzle.regions.indices) {
            if (!candidates.isOpen(x)) continue
            val violation = HallViolation.smallest(puzzle, candidates.place(puzzle, x))?.takeIf { it.k >= 2 } ?: continue
            causes.getOrPut(violation, ::mutableSetOf) += x
        }
        return causes.entries
            .sortedWith(compareBy({ it.key.kind }, { it.key.units.first() }, { it.value.min() }))
            .asSequence()
            .map { (violation, cells) ->
                val unit = HintReason(violation.kind, violation.units.first(), this)
                val evidence = violation.cells(puzzle).filter { candidates.isOpen(it) && it !in cells }.toSet()
                QueensProof(QueensDeduction.Exclude(this, unit, cells), evidence)
            }
    }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction
}
