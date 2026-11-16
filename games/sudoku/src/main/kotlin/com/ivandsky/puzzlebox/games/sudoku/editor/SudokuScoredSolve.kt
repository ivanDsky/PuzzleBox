package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.EliminationSteps
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates

/**
 * One scored solve of a board (92), walked the way a player goes: place the easiest single on
 * the board, and only when there is none strike candidates with the easiest elimination that
 * applies, until a single shows. Strikes are kept, as pencil marks are, so the walk gets at
 * least as far as the hint ladder, which works its candidates out again for every placement.
 *
 * The [SudokuSolveTally] counts every placement by its kind and each time the walk was stuck
 * by the hardest elimination that got it going again, and takes the cost of spotting each.
 */
internal class SudokuScoredSolve private constructor(private val geometry: SudokuGeometry, givens: IntArray) {
    private val candidates = SudokuCandidates.ofKnown(geometry, givens)
    private var open = givens.count { it == 0 }

    /** Per cell, the easiest single the last [scan] found on it and its digit; null where there is none. */
    private val singles = arrayOfNulls<SudokuSolveStep>(geometry.cellCount)
    private val digits = IntArray(geometry.cellCount)

    private fun run(budget: Budget): SudokuSolveTally? {
        val tally = SudokuSolveTally()
        var unstuckBy: SudokuSolveStep? = null
        while (open > 0) {
            if (budget.exhausted()) return null
            val moves = scan()
            if (moves == 0) {
                val strike = EliminationSteps.DEFAULT.firstOrNull { it.eliminate(geometry, candidates) } ?: return tally
                val step = SudokuSolveStep.striking(strike.technique)
                if (unstuckBy == null) tally.spot(open, moves)
                unstuckBy = maxOf(unstuckBy ?: step, step)
                continue
            }
            unstuckBy?.let(tally::add)
            unstuckBy = null
            val cell = singles.indices.minBy { singles[it]?.ordinal ?: Int.MAX_VALUE }
            tally.add(checkNotNull(singles[cell]))
            tally.spot(open, moves)
            place(cell, digits[cell])
        }
        tally.solved = true
        return tally
    }

    /** Fills [singles] and [digits] from the candidates; the number of cells a single places. */
    private fun scan(): Int {
        for (cell in candidates.indices) {
            val mask = candidates[cell]
            singles[cell] = if (Integer.bitCount(mask) == 1) SudokuSolveStep.NAKED_SINGLE else null
            digits[cell] = Integer.numberOfTrailingZeros(mask) + 1
        }
        for (unit in geometry.units) {
            val cells = geometry.cellsOf(unit)
            var once = 0
            var twice = 0
            var openCells = 0
            for (cell in cells) {
                val mask = candidates[cell]
                if (mask != 0) openCells++
                twice = twice or (once and mask)
                once = once or mask
            }
            val hidden = once and twice.inv()
            if (hidden == 0) continue
            val step = when {
                openCells == 1 -> SudokuSolveStep.LAST_CELL
                unit.kind == UnitKind.BOX -> SudokuSolveStep.BOX_SINGLE
                else -> SudokuSolveStep.LINE_SINGLE
            }
            for (cell in cells) {
                val digit = candidates[cell] and hidden
                val found = singles[cell]
                if (digit == 0 || (found != null && found <= step)) continue
                singles[cell] = step
                digits[cell] = Integer.numberOfTrailingZeros(digit) + 1
            }
        }
        return singles.count { it != null }
    }

    private fun place(cell: Int, digit: Int) {
        val keep = (1 shl (digit - 1)).inv()
        candidates[cell] = 0
        for (peer in geometry.peersOf(cell)) candidates[peer] = candidates[peer] and keep
        open--
    }

    companion object {
        /**
         * The tally of solving [givens], which it only reads; null once [budget] is spent,
         * polled before every step. A board the walk cannot finish comes back not solved.
         */
        fun of(geometry: SudokuGeometry, givens: IntArray, budget: Budget): SudokuSolveTally? =
            SudokuScoredSolve(geometry, givens).run(budget)
    }
}
