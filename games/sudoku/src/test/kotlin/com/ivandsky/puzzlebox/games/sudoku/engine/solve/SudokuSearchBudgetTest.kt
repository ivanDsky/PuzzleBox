package com.ivandsky.puzzlebox.games.sudoku.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSearchBudgetTest {

    private val mini = SudokuVariants.MINI.geometry
    private val classic = SudokuVariants.CLASSIC.geometry

    @Test
    fun `a board with two solutions returns both`() {
        val puzzle = KnownSolutions.peanut450
        val givens = puzzle.givens.toMutableList().also { it[2] = 0 }

        val outcome = SudokuSolver.bounded(puzzle.geometry, givens, limit = 2, budget = Budget.Unlimited)

        val solutions = (outcome as SudokuSearchOutcome.Done).solutions
        assertEquals(2, solutions.size)
        assertNotEquals(solutions[0], solutions[1])
        assertTrue(puzzle.solution in solutions)
        solutions.forEach { assertCompletes(puzzle.geometry, givens, it) }
    }

    @Test
    fun `a unique board returns its single solution`() {
        val puzzle = KnownSolutions.classicSample

        val outcome = SudokuSolver.bounded(puzzle.geometry, puzzle.givens, limit = 2, budget = Budget.Unlimited)

        assertEquals(SudokuSearchOutcome.Done(listOf(puzzle.solution)), outcome)
    }

    @Test
    fun `clashing givens settle with no solutions`() {
        val clashInRow = List(mini.cellCount) { if (it == 0 || it == 5) 3 else 0 }

        assertEquals(SudokuSearchOutcome.Done(emptyList()), SudokuSolver.bounded(mini, clashInRow, 2, Budget.Unlimited))
    }

    @Test
    fun `an exhausted budget answers Exhausted at the first poll`() {
        var polls = 0
        val spent = Budget { polls++; true }

        val outcome = SudokuSolver.bounded(classic, List(classic.cellCount) { 0 }, limit = 2, budget = spent)

        assertEquals(SudokuSearchOutcome.Exhausted, outcome)
        assertEquals(1, polls)
    }

    @Test
    fun `cancelling mid-search stops at the next node with Exhausted`() {
        var polls = 0
        val budget = Budget.within(timeoutMs = Long.MAX_VALUE / NANOS_PER_MILLI, nanoTime = { 0L }, cancelled = { ++polls > 10 })

        val outcome = SudokuSolver.bounded(classic, List(classic.cellCount) { 0 }, limit = 2, budget = budget)

        assertEquals(SudokuSearchOutcome.Exhausted, outcome)
        assertEquals(11, polls)
    }

    @Test
    fun `the empty 6x6 board settles within the editor budget`() {
        val outcome = withinEditorBudget(mini, List(mini.cellCount) { 0 })

        assertEquals(2, (outcome as SudokuSearchOutcome.Done).solutions.size)
    }

    @Test
    fun `the empty 9x9 board settles within the editor budget`() {
        val outcome = withinEditorBudget(classic, List(classic.cellCount) { 0 })

        assertEquals(2, (outcome as SudokuSearchOutcome.Done).solutions.size)
    }

    @Test
    fun `a sparse 17-clue 9x9 board settles within the editor budget`() {
        val givens = digits(SEVENTEEN_CLUES)
        assertEquals(17, givens.count { it != 0 })

        val outcome = withinEditorBudget(classic, givens)

        assertEquals(SudokuSearchOutcome.Done(listOf(digits(SEVENTEEN_CLUES_SOLUTION))), outcome)
    }

    private fun withinEditorBudget(geometry: SudokuGeometry, givens: List<Int>): SudokuSearchOutcome {
        val start = System.nanoTime()
        val outcome = SudokuSolver.bounded(geometry, givens, limit = 2, budget = Budget.within(BUDGET_MS, System::nanoTime))
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI
        assertTrue("took $elapsedMs ms", elapsedMs <= BUDGET_MS)
        return outcome
    }

    private fun assertCompletes(geometry: SudokuGeometry, givens: List<Int>, solution: List<Int>) {
        givens.forEachIndexed { idx, digit -> if (digit != 0) assertEquals(digit, solution[idx]) }
        val units = (0 until geometry.cellCount).groupBy { geometry.rowOf(it) }.values +
            (0 until geometry.cellCount).groupBy { geometry.colOf(it) }.values +
            (0 until geometry.cellCount).groupBy { geometry.boxOf(it) }.values
        units.forEach { unit -> assertEquals((1..geometry.size).toSet(), unit.map { solution[it] }.toSet()) }
    }

    private fun digits(board: String): List<Int> = board.map { it.digitToInt() }

    private companion object {
        const val BUDGET_MS = 1_500L
        const val NANOS_PER_MILLI = 1_000_000L
        const val SEVENTEEN_CLUES = "000000010400000000020000000000050407008000300001090000300400200050100000000806000"
        const val SEVENTEEN_CLUES_SOLUTION =
            "693784512487512936125963874932651487568247391741398625319475268856129743274836159"
    }
}
