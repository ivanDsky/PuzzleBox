package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle

/**
 * Cuts Trainer snippets from a puzzle's walk ([SudokuTechniqueWalker]): the boards the walk met just
 * before a step of one technique. [SudokuWalkStep] stays internal; a cut hands out the board, the
 * struck candidates and the step only. A reveal is never cut: no technique explains it.
 */
object SudokuSnippets {

    /**
     * Up to [count] cuts of [puzzle] for [technique], in walk order. When the walk has more target
     * steps than [count], the cuts are spread over them: the first, the last, and evenly spaced steps
     * between (the first alone for a [count] of 1). The same puzzle always cuts the same boards.
     *
     * Empty when [technique] is no Sudoku technique, when the walk never uses it, and when the
     * puzzle's stored solution is not its unique solution.
     *
     * @throws IllegalArgumentException when [count] is negative.
     */
    fun cut(puzzle: SudokuPuzzle, technique: TechniqueId, count: Int): List<SudokuSnippetCut> {
        require(count >= 0) { "count must not be negative: $count" }
        if (count == 0 || technique !in SudokuTechniqueIds.all) return emptyList()
        if (!SudokuTechniqueWalker.hasUniqueSolution(puzzle)) return emptyList()
        val cuts = SudokuTechniqueWalker.walk(puzzle)
            .filter { it.technique == technique }
            .map { SudokuSnippetCut(it.boardBefore, it.struckBefore, it.step) }
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
