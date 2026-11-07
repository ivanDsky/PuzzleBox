package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSearchOutcome
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver

/**
 * A Sudoku draft → its [Verdict]. A board with no givens is Incomplete; otherwise the solver
 * looks for up to two solutions, so Unique, Multiple and None are exact. Multiple carries both
 * witnesses and the cells they differ on. Givens that share a row, column or box are None with
 * the clashing cells, named before any search; a plain no-solution None carries no cells.
 * Unique is graded by [SudokuGrader]: a difficulty label, or `needsGuessing` when the hint
 * ladder has to reveal a cell. A spent or cancelled [Budget] reads as [Verdict.Undetermined].
 */
object SudokuDraftAnalyzer {
    const val EMPTY: String = "sudoku.empty"   // count 0: no givens

    /** Every IncompleteReason id [analyze] can emit, so a copy table can prove it covers them all. */
    val reasonIds: Set<String> = setOf(EMPTY)

    fun analyze(draft: SudokuDraft, geometry: SudokuGeometry, budget: Budget): Verdict {
        if (draft.givens.all { it == 0 }) return Verdict.Incomplete(IncompleteReason(EMPTY, 0))
        val board = SudokuBoard(geometry, draft.givens.map { if (it == 0) SudokuCell.Empty else SudokuCell.Given(it) })
        val clashes = SudokuConflicts.find(board)
        if (clashes.isNotEmpty()) return Verdict.None(SudokuConflicts.cellsIn(clashes))
        val outcome = SudokuSolver.bounded(geometry, draft.givens, limit = 2, budget = budget)
        if (outcome !is SudokuSearchOutcome.Done) return Verdict.Undetermined
        val found = outcome.solutions
        return when (found.size) {
            0 -> Verdict.None(emptySet())
            1 -> unique(geometry, draft, found[0])
            else -> {
                val (a, b) = found
                Verdict.Multiple(Solution(a), Solution(b), a.indices.filterTo(mutableSetOf()) { a[it] != b[it] })
            }
        }
    }

    private fun unique(geometry: SudokuGeometry, draft: SudokuDraft, solution: List<Int>): Verdict.Unique {
        val variant = SudokuVariants.all.first { it.geometry == geometry }
        val grade = SudokuGrader.grade(SudokuPuzzle(variant, draft.givens, solution))
        return if (grade.needsGuessing) {
            Verdict.Unique(Solution(solution), needsGuessing = true)
        } else {
            Verdict.Unique(Solution(solution), difficulty = grade.hardest?.let(::Difficulty))
        }
    }
}
