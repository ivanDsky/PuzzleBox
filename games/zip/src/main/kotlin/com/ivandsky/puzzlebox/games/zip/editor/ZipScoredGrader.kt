package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.LevelBands
import com.ivandsky.puzzlebox.game.editor.ScoredGrader
import com.ivandsky.puzzlebox.game.editor.Solution

/**
 * Zip's scored difficulty (ticket 94): the points of every wrong move a solver has to rule out
 * along the answer ([ZipSolveTrace]), ten times over and per board side. Per side, so that a
 * large board is harder than a small one that is as open, yet every size from 6×6 can be made at
 * every level. Walls and numbers both take wrong moves away, so more of either scores lower.
 * Zip has no ladder of techniques; the label carries no rung. A board whose trace is not done
 * within the analyzer's node cap, or the budget, shows no level.
 */
object ZipScoredGrader : ScoredGrader<ZipDraft> {
    /**
     * Where Zip's levels start, for the grader and for [ZipCompleter] aiming at one. Set from
     * `ZipScoreMeasurement`: the captured LinkedIn dailies with walls score 30, 56 and 73, so a
     * daily is Easy or Medium and at most just Hard (one 7×7 capture with nine numbers and no
     * wall scores 131); Finish for me with no level asked scores 32 to 63 at the median, Medium
     * from 6×6 up; and a board with its spare numbers and walls taken off again reaches 100 at
     * 6×6 and larger, hardly ever at 5×5.
     */
    val BANDS: LevelBands = LevelBands(medium = 40, hard = 70, expert = 100)

    private const val SCALE = 10

    override fun grade(draft: ZipDraft, solution: Solution, budget: Budget): Difficulty? {
        if (draft.waypoints.size < 2) return null
        val trace = ZipSolveTrace(draft, solution.values, budget, ZipDraftAnalyzer.MAX_NODES)
        return if (trace.walk() == ZipSolveTrace.Outcome.Unique) BANDS.difficulty(score(draft.n, trace.points)) else null
    }

    /** The score of a board of side [n] whose wrong moves cost [points] in all. */
    internal fun score(n: Int, points: Int): Int = SCALE * points / n
}
