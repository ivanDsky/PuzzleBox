package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.LevelBands
import com.ivandsky.puzzlebox.game.editor.ScoredGrader
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.AttackRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.SinglesRung

/**
 * Queens' scored difficulty (91): one number for the whole solve, on one scale for every size.
 * The board is solved from empty the way the step hints solve it, always by the easiest step on
 * offer, and
 *
 *     score = side × (sum of the steps' weights + queens that were the only obvious move)
 *
 * - A step weighs by its rung's level: placing a queen 1, a Medium exclusion (line lock, shared
 *   neighbours) 3, a Hard one (the pigeonhole family) 6, a look-ahead 15.
 * - A queen placed while no other queen could be placed counts once more: with one obvious move
 *   on the board there is nothing else to fall back on.
 * - The side multiplies, because every step means reading that many rows, columns and regions.
 *
 * The legacy label is the hardest rung alone, so one look-ahead made a board Expert and a long
 * board of line locks stayed Medium. No flag is read here: the Studio asks for this grade only
 * under scored grading, and a published puzzle keeps the label it was stored with.
 */
object QueensScoredGrader : ScoredGrader<QueensDraft> {

    /**
     * Where Medium, Hard and Expert start; [QueensCompleter] aims with the same numbers. Set from
     * `QueensScoredMeasurement` so that the 806 daily boards split 23, 41, 27 and 9 percent. On an
     * 8×8 that is 23, 44 and 67 weighted steps. A 4×4 never leaves Easy and a 5×5 never reaches
     * Hard, while a 12×12 is almost never Easy: the score counts the size.
     */
    val Bands: LevelBands = LevelBands(medium = 180, hard = 350, expert = 530)

    private const val ONLY_MOVE = 1

    /** The step-hint ladder without its Auto-cross rung, which lists nothing on a board solved from empty. */
    private val ladder = QueensLadder(QueensLadder.Stepwise.rungs - AttackRung)

    override fun grade(draft: QueensDraft, solution: Solution, budget: Budget): Difficulty? =
        grade(QueensPuzzle(draft.size, draft.regions), budget)

    /**
     * [puzzle]'s score, level and hardest rung. Null when [budget] is spent before the last queen
     * stands (it is polled between steps), and when the steps stall, which a board with one answer
     * that the legacy ladder finishes did not do once in the measurement.
     */
    internal fun grade(puzzle: QueensPuzzle, budget: Budget): Difficulty? {
        var candidates = QueensCandidates.start(puzzle, placed = emptySet())
        var weighted = 0
        var hardest = 0
        while (candidates.queens.size < puzzle.size) {
            if (budget.exhausted()) return null
            val deduction = ladder.next(puzzle, candidates) ?: return null
            val rung = deduction.unit.rung
            weighted += weight(rung.level)
            if (rung === SinglesRung && SinglesRung.cells(puzzle, candidates).size == 1) weighted += ONLY_MOVE
            hardest = maxOf(hardest, ladder.rungs.indexOf(rung))
            candidates = when (deduction) {
                is QueensDeduction.Place -> candidates.place(puzzle, deduction.cell)
                is QueensDeduction.Exclude -> candidates.exclude(deduction.cells)
            }
        }
        return Bands.difficulty(puzzle.size * weighted, ladder.rungs[hardest])
    }

    private fun weight(level: Difficulty.Level): Int = when (level) {
        Difficulty.Level.Easy -> 1
        Difficulty.Level.Medium -> 3
        Difficulty.Level.Hard -> 6
        Difficulty.Level.Expert -> 15
    }
}
