package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * Cuts Trainer snippets from a puzzle's walk ([TangoTechniqueWalker]): the grids the walk met just
 * before a step of one technique. [TangoWalkStep] stays internal; a cut hands out the grid and the
 * step only.
 *
 * Only a step the technique's own rung proved is cut, so a snippet is never cut before a reveal (a
 * cell no rung can explain).
 */
object TangoSnippets {

    /**
     * Up to [count] cuts of [puzzle] for [technique], in walk order. [solution] is the puzzle's unique
     * solution ([com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.solve]), which the caller
     * solves once and carries: a cut never solves. When the walk has more target steps than [count],
     * the cuts are spread over them: the first, the last, and evenly spaced steps between (the first
     * alone for a [count] of 1). The same puzzle always cuts the same grids.
     *
     * Empty when [technique] is no Tango technique, when the walk never uses it, and when [solution]
     * is no solution of [puzzle] to walk by.
     *
     * @throws IllegalArgumentException when [count] is negative.
     */
    fun cut(puzzle: TangoPuzzle, solution: List<TangoCell>, technique: TechniqueId, count: Int): List<TangoSnippetCut> {
        require(count >= 0) { "count must not be negative: $count" }
        if (count == 0 || TangoStepRung.entries.none { it.trainerId == technique }) return emptyList()
        if (!isSolution(puzzle, solution)) return emptyList()
        val cuts = TangoTechniqueWalker.walk(puzzle, solution).mapNotNull { walked ->
            val step = walked.step as? TangoStep.Deduction
            if (step != null && walked.technique == technique) TangoSnippetCut(walked.gridBefore, step) else null
        }
        return spread(cuts, count)
    }

    /** [solution] fills every cell, keeps every given and breaks no rule. */
    private fun isSolution(puzzle: TangoPuzzle, solution: List<TangoCell>): Boolean =
        solution.size == puzzle.cellCount &&
            puzzle.givens.all { (idx, value) -> solution[idx] == value } &&
            TangoRules.isComplete(TangoState(puzzle, solution))

    /** [count] of [all], first to last and evenly spaced; all of them when there are no more than [count]. */
    private fun <T> spread(all: List<T>, count: Int): List<T> = when {
        all.size <= count -> all
        count == 1 -> listOf(all.first())
        // Strictly increasing: all.size > count makes the stride above 1, so no step is picked twice.
        else -> List(count) { i -> all[i * all.lastIndex / (count - 1)] }
    }
}
