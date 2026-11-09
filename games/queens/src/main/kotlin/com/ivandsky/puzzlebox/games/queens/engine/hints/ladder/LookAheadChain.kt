package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * The what-if behind D10 and D11 (lab `chain`): stand a queen on one open cell and play the
 * board forward a round at a time until a [HallViolation] shows it cannot stand there. A round
 * stands every current [SinglesRung] queen at once; when there is none it takes the first
 * deduction of the first cheap rung that has one. A forced queen that an earlier one of the
 * same round rules out is not stood, so the unit that forced it is left empty: a collision is
 * reported as that emptied unit, at the round that forced both.
 */
internal object LookAheadChain {

    /** Rounds after which a chain that has not broken proves nothing (lab `CHAIN_CAP`). */
    const val CAP = 24

    /** The queen on [cell] broke as [violation] after [rounds] rounds, having [forced] these queens in order. */
    data class Break(val cell: Int, val rounds: Int, val forced: List<Int>, val violation: HallViolation.Violation) {

        /**
         * [rung]'s exclusion of [cell], naming the violation's lowest unit. The evidence is the
         * violation's cells still open in [candidates] plus the forced queens, never [cell].
         */
        fun proof(rung: QueensRung, puzzle: QueensPuzzle, candidates: QueensCandidates): QueensProof {
            val unit = HintReason(violation.kind, violation.units.first(), rung)
            val evidence = violation.cells(puzzle).filter(candidates::isOpen).toSet() + forced - cell
            return QueensProof(QueensDeduction.Exclude(rung, unit, setOf(cell)), evidence)
        }
    }

    /**
     * Every open cell whose chain breaks within [cap] rounds, fewest rounds first, then lowest
     * cell. One [HallViolation.Scratch] serves every chain of the call.
     */
    fun breaks(puzzle: QueensPuzzle, candidates: QueensCandidates, cheap: List<QueensRung>, cap: Int): List<Break> {
        val scratch = HallViolation.Scratch(puzzle)
        return puzzle.regions.indices
            .filter(candidates::isOpen)
            .mapNotNull { run(puzzle, candidates, it, cheap, scratch, cap) }
            .sortedWith(compareBy({ it.rounds }, { it.cell }))
    }

    /** The chain from a queen on the open [cell]: its break within [cap] rounds, or null when it stalls or runs out of rounds. */
    fun run(
        puzzle: QueensPuzzle,
        candidates: QueensCandidates,
        cell: Int,
        cheap: List<QueensRung>,
        scratch: HallViolation.Scratch = HallViolation.Scratch(puzzle),
        cap: Int = CAP,
    ): Break? {
        var state = candidates.place(puzzle, cell)
        val forced = ArrayList<Int>()
        var rounds = 0
        while (true) {
            HallViolation.smallest(state, scratch)?.let { return Break(cell, rounds, forced, it) }
            if (rounds == cap) return null
            state = round(puzzle, state, cheap, forced) ?: return null
            rounds++
        }
    }

    /** One round from [state], adding the queens it stands to [forced]; null when nothing applies. */
    private fun round(puzzle: QueensPuzzle, state: QueensCandidates, cheap: List<QueensRung>, forced: MutableList<Int>): QueensCandidates? {
        val singles = SinglesRung.steps(puzzle, state).map { (it.deduction as QueensDeduction.Place).cell }.distinct().toList()
        if (singles.isNotEmpty()) {
            var next = state
            for (cell in singles) {
                if (!next.isOpen(cell)) continue
                next = next.place(puzzle, cell)
                forced += cell
            }
            return next
        }
        return when (val deduction = cheap.firstNotNullOfOrNull { it.step(puzzle, state) } ?: return null) {
            is QueensDeduction.Place -> state.place(puzzle, deduction.cell).also { forced += deduction.cell }
            is QueensDeduction.Exclude -> state.exclude(deduction.cells)
        }
    }
}
