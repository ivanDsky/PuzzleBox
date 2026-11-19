package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier

/**
 * Cuts Trainer snippets from a puzzle's walk ([QueensTechniqueWalker]): the boards the walk met just
 * before a step of one technique. [QueensWalkStep] stays internal; a cut hands out the board and the
 * step only.
 *
 * Only a step the technique's own rung proved is cut, so a snippet is never cut before a reveal (a
 * cross no rung can explain) and never before an Attack step (no Trainer technique).
 */
object QueensSnippets {

    /**
     * Up to [count] cuts of [puzzle] for [technique], in walk order. When the walk has more target
     * steps than [count], the cuts are spread over them: the first, the last, and evenly spaced steps
     * between (the first alone for a [count] of 1). The same puzzle always cuts the same boards.
     *
     * Empty when [technique] is no Queens technique, when the walk never uses it, and when [puzzle]
     * has no true solution to walk by, the cases [QueensTechniqueWalker.techniqueCounts] counts nothing for.
     *
     * @throws IllegalArgumentException when [count] is negative.
     */
    fun cut(puzzle: QueensPuzzle, technique: TechniqueId, count: Int): List<QueensSnippetCut> {
        require(count >= 0) { "count must not be negative: $count" }
        if (count == 0 || QueensLadder.Stepwise.rungs.none { it.trainerId == technique }) return emptyList()
        val solution = puzzle.solution ?: return emptyList()
        if (!QueensVerifier.isSolution(puzzle, solution.toSet())) return emptyList()
        val cuts = QueensTechniqueWalker.walk(puzzle).mapNotNull { walked ->
            val step = walked.step as? QueensStep.Deduction
            if (step != null && walked.technique == technique) QueensSnippetCut(walked.boardBefore, step) else null
        }
        return spread(cuts, count)
    }

    /** [count] of [all], first to last and evenly spaced; all of them when there are no more than [count]. */
    private fun <T> spread(all: List<T>, count: Int): List<T> = when {
        all.size <= count -> all
        count == 1 -> listOf(all.first())
        // Strictly increasing: all.size > count makes the stride above 1, so no step is picked twice.
        else -> List(count) { i -> all[i * all.lastIndex / (count - 1)] }
    }
}
