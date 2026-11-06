package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * The Queens hint ladder (spec A9). [rungs] run easiest first; their list order is the
 * difficulty order, so the hardest rung a run needs is the last-indexed one it used.
 */
class QueensLadder(private val rungs: List<QueensRung>) {

    /** Lowest rung first; restart from the first rung after every deduction. */
    fun run(puzzle: QueensPuzzle, placed: Set<Int> = emptySet()): QueensLadderRun {
        var candidates = QueensCandidates.start(puzzle, placed)
        var hardest = -1
        var steps = 0
        while (true) {
            val deduction = next(puzzle, candidates) ?: break
            candidates = when (deduction) {
                is QueensDeduction.Place -> candidates.place(puzzle, deduction.cell)
                is QueensDeduction.Exclude -> candidates.exclude(deduction.cells)
            }
            hardest = maxOf(hardest, rungs.indexOf(deduction.rung))
            steps++
        }
        return QueensLadderRun(
            solved = candidates.queens.size == puzzle.size * puzzle.queensPerUnit,
            hardest = rungs.getOrNull(hardest),
            steps = steps,
        )
    }

    /** The first deduction of the lowest rung that has one, or null when every rung stalls. */
    fun next(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        rungs.firstNotNullOfOrNull { it.step(puzzle, candidates) }

    companion object {
        private val propagation: List<QueensRung> = listOf(SinglesRung, LineLockRung, PigeonholeRung)

        /** All four rungs in difficulty order; look-ahead probes with the three below it. */
        val Default: QueensLadder = QueensLadder(propagation + LookAheadRung(propagation))
    }
}
