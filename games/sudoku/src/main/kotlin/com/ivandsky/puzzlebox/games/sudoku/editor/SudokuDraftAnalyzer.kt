package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSearchOutcome
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver

/**
 * A Sudoku draft → its [Verdict]. A board with no givens is Incomplete; otherwise the solver
 * looks for up to two solutions, so Unique, Multiple and None are exact. Multiple carries both
 * witnesses and the cells they differ on; None carries no cells. A spent or cancelled [Budget]
 * reads as [Verdict.Undetermined].
 */
object SudokuDraftAnalyzer {
    const val EMPTY: String = "sudoku.empty"   // count 0: no givens

    /** Every IncompleteReason id [analyze] can emit, so a copy table can prove it covers them all. */
    val reasonIds: Set<String> = setOf(EMPTY)

    fun analyze(draft: SudokuDraft, geometry: SudokuGeometry, budget: Budget): Verdict {
        if (draft.givens.all { it == 0 }) return Verdict.Incomplete(IncompleteReason(EMPTY, 0))
        val outcome = SudokuSolver.bounded(geometry, draft.givens, limit = 2, budget = budget)
        if (outcome !is SudokuSearchOutcome.Done) return Verdict.Undetermined
        val found = outcome.solutions
        return when (found.size) {
            0 -> Verdict.None(emptySet())
            1 -> Verdict.Unique(Solution(found[0]))
            else -> {
                val (a, b) = found
                Verdict.Multiple(Solution(a), Solution(b), a.indices.filterTo(mutableSetOf()) { a[it] != b[it] })
            }
        }
    }
}
