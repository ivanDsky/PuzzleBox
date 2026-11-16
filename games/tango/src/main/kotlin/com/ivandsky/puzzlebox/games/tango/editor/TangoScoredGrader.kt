package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.ScoredGrader
import com.ivandsky.puzzlebox.game.editor.Solution

/**
 * Tango's scored difficulty (93): the whole solve weighed, not its hardest step alone. The legacy
 * [TangoGrader] calls a board Hard as soon as one cell needs a line count, which nearly every board
 * does, so its label says little about how long the board takes.
 *
 * [TangoSolveWalk] walks the hint ladder and the score adds up every cell it places: 1 for a cell a
 * marker gives, 2 for a three-in-a-row, 3 for a line count and `n + 4` for a cell only the legal
 * fills of a line give, the one step that takes working a whole line through, and the longer the
 * line the more there is to work through. Each narrow step (one move on offer anywhere on the
 * board, see [TangoSolveWalk]) adds `n / 2`: the solver has to find it, on a board that grows with
 * [TangoDraft.n]. Board size also counts through the number of cells to place. The level is read
 * from the size's [TangoScoreBands], which [TangoTargetFinish] aims with too.
 */
object TangoScoredGrader : ScoredGrader<TangoDraft> {
    private const val MARKER = 1
    private const val TRIPLE = 2
    private const val LINE_FULL = 3

    /** Line fills cost [LINE_FILLS_BASE] plus the line's length. */
    private const val LINE_FILLS_BASE = 4

    /** Null for a full board, which has no step to weigh, and when [budget] is spent. */
    override fun grade(draft: TangoDraft, solution: Solution, budget: Budget): Difficulty? {
        val tally = TangoSolveWalk(draft.n, draft.markers).tally(draft.givens, budget) ?: return null
        return difficulty(draft.n, tally)
    }

    /** The label of a board of width [n] the ladder finished as [tally] counts; null when it placed nothing. */
    internal fun difficulty(n: Int, tally: TangoSolveTally): Difficulty? {
        val hardest = tally.hardest ?: return null
        return TangoScoreBands.of(n).difficulty(score(n, tally), hardest)
    }

    internal fun score(n: Int, tally: TangoSolveTally): Int =
        MARKER * tally.marker + TRIPLE * tally.triple + LINE_FULL * tally.lineFull +
            (n + LINE_FILLS_BASE) * tally.lineFills + n / 2 * tally.narrow
}
