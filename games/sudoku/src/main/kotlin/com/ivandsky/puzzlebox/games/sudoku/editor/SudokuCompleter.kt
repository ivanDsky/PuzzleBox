package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.DraftCompleter
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuRandomFill
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSearchOutcome
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import kotlin.random.Random

/**
 * Sudoku's Finish for me (spec E1): adds givens to a draft until the board has one solution
 * the hint ladder can reach, keeping every given the author placed. [SudokuRandomFill] draws
 * one full grid that agrees with the givens; every added given is a digit of that grid. Givens
 * go first where two solutions differ, until one is left, then on a cell the ladder cannot
 * reach, until it finishes the board. A board is returned only then, which is what
 * [SudokuDraftAnalyzer] calls Unique and not needs-guessing. Asked for a level, it hands
 * that board to [SudokuTargetFinish] to tune.
 */
object SudokuCompleter : DraftCompleter<SudokuDraft> {
    /** [FinishReadiness.NeedsMore] reason; IncompleteReason.count = givens the author still has to place. */
    const val GIVENS: String = "sudoku.finish.givens"

    /** Givens a 6×6 draft must hold before Finish for me is offered. */
    const val MIN_GIVENS_MINI: Int = 1

    /** Givens a 9×9 draft must hold before Finish for me is offered. */
    const val MIN_GIVENS_CLASSIC: Int = 1

    /**
     * Search nodes the solver may visit looking for two solutions before the search counts as
     * stalled. On a 9×9 board a whole completion takes about 4 000 nodes over some 30
     * searches, but one in a few hundred meets a search of millions and would spend the whole
     * budget in it (SudokuCompleterMeasurement). A count of nodes, not time, so a seed gives
     * the same board on any device.
     */
    private const val STALL_NODES = 10_000

    private val hints = SudokuHints()

    fun minimumGivens(size: Int): Int =
        if (size == SudokuVariants.MINI.geometry.size) MIN_GIVENS_MINI else MIN_GIVENS_CLASSIC

    /** Blocked on givens that share a row, column or box ([SudokuConflicts]); NeedsMore below [minimumGivens]. */
    override fun readiness(draft: SudokuDraft): FinishReadiness {
        val board = SudokuBoard(geometryOf(draft), draft.givens.map { if (it == 0) SudokuCell.Empty else SudokuCell.Given(it) })
        val clashes = SudokuConflicts.find(board)
        if (clashes.isNotEmpty()) return FinishReadiness.Blocked(SudokuConflicts.cellsIn(clashes))
        val missing = minimumGivens(draft.size) - draft.givens.count { it != 0 }
        return if (missing > 0) FinishReadiness.NeedsMore(IncompleteReason(GIVENS, missing)) else FinishReadiness.Ready
    }

    /** With no [target] the first board found, as before levels; with one, the board [SudokuTargetFinish] tunes to it (92). */
    override fun complete(draft: SudokuDraft, random: Random, budget: Budget, target: Difficulty.Level?): Completion<SudokuDraft> =
        if (target == null) first(draft, random, budget) else SudokuTargetFinish.complete(draft, target, random, budget)

    /** The first board the ladder finishes, whatever its level. */
    internal fun first(draft: SudokuDraft, random: Random, budget: Budget): Completion<SudokuDraft> {
        val geometry = geometryOf(draft)
        val grid = SudokuRandomFill.fill(geometry, draft.givens, random, budget) ?: return Completion.GaveUp
        val givens = draft.givens.toIntArray()
        if (!narrowToOne(geometry, givens, grid, random, budget)) return Completion.GaveUp
        while (true) {
            val open = unreached(geometry, givens, budget) ?: return Completion.GaveUp
            if (open.isEmpty()) return Completion.Done(SudokuDraft(draft.size, givens.toList()))
            val cell = open[random.nextInt(open.size)]
            givens[cell] = grid[cell]
        }
    }

    /**
     * Adds digits of [grid] to [givens] until the solver finds one solution only, each on a
     * cell drawn from those where two solutions differ; after a search that stalled
     * ([STALL_NODES]), on any open cell, which leaves the next search less to do. False once
     * [budget] is spent; the solver polls it at every search node.
     */
    internal fun narrowToOne(geometry: SudokuGeometry, givens: IntArray, grid: List<Int>, random: Random, budget: Budget): Boolean {
        while (true) {
            var nodes = 0
            val slice = Budget { ++nodes > STALL_NODES || budget.exhausted() }
            val outcome = SudokuSolver.bounded(geometry, givens.asList(), limit = 2, budget = slice)
            val found = (outcome as? SudokuSearchOutcome.Done)?.solutions
            val cells = when {
                found == null -> if (nodes > STALL_NODES) givens.indices.filter { givens[it] == 0 } else return false
                found.size < 2 -> return true
                else -> found[0].indices.filter { found[0][it] != found[1][it] }
            }
            val cell = cells[random.nextInt(cells.size)]
            givens[cell] = grid[cell]
        }
    }

    /**
     * The cells the hint ladder leaves open from [givens], ascending: empty when it finishes
     * the board, null once [budget] is spent. The ladder is walked one placement at a time,
     * as [SudokuGrader] walks it, with the budget polled between placements.
     */
    internal fun unreached(geometry: SudokuGeometry, givens: IntArray, budget: Budget): List<Int>? {
        val known = givens.copyOf()
        while (!budget.exhausted()) {
            val place = hints.placement(geometry, known) ?: return known.indices.filter { known[it] == 0 }
            known[place.cell] = place.digit
        }
        return null
    }

    private fun geometryOf(draft: SudokuDraft): SudokuGeometry = requireNotNull(SudokuVariants.bySize(draft.size)).geometry
}
