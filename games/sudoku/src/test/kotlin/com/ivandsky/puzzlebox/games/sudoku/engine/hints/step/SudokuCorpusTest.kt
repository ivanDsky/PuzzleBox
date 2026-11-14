package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SudokuStepper] re-run over the Mini Sudoku corpus (catalog SC1, SC2; `sudoku-deductions.md`).
 *
 * Real boards: 454 LinkedIn Mini Sudoku dailies, #1 (2025-08-12) to #454 (2026-11-08), taken from
 * linkedinzip.solutions (same data as awa.games) on 2026-11-08 and cross-checked against
 * linkedinpinpointanswer.today (280 of 280 agree). Built by `.scratch/hint-parity/corpus/sudoku/build.js`,
 * vendored as `fixtures/sudoku-boards.json`. Their 10954 empty cells are the 10954 solve steps.
 *
 * Generated boards: 2000 seeded random unique boards (`lab/sudoku/genrandom.js 2000 1`, ids `rnd:<k>`),
 * vendored as `fixtures/sudoku-generated.json`. They are stress boards for the rare rungs and are never
 * presented as LinkedIn boards.
 */
class SudokuCorpusTest {

    @Serializable
    private data class RealBoard(val id: String, val n: Int, val date: String, val givens: String, val solution: String)

    @Serializable
    private data class GeneratedBoard(val id: String, val givens: String, val solution: String)

    /** What one board's rerun placed and struck, so the corpus tests can assert the rung counts. */
    private class Rerun(var placements: Int = 0, var eliminations: Int = 0, var reveals: Int = 0)

    @Test
    fun `real boards rerun to Solved with one placement per empty cell and no elimination or reveal`() {
        var placements = 0
        for (board in realBoards()) {
            val tally = rerun(board.id, puzzleOf(board.givens, board.solution))
            assertEquals("${board.id}: eliminations", 0, tally.eliminations)
            assertEquals("${board.id}: reveals", 0, tally.reveals)
            placements += tally.placements
        }
        assertEquals(10954, placements)
    }

    @Test
    fun `every real board has exactly one solution, the recorded one`() {
        for (board in realBoards()) assertUniqueSolution(board.id, puzzleOf(board.givens, board.solution))
    }

    @Test
    fun `generated boards rerun to Solved with no unsound step, and an elimination and a reveal both fire`() {
        var eliminations = 0
        var reveals = 0
        for (board in generatedBoards()) {
            val tally = rerun(board.id, puzzleOf(board.givens, board.solution))
            eliminations += tally.eliminations
            reveals += tally.reveals
        }
        assertTrue("no elimination on any generated board", eliminations > 0)
        assertTrue("no reveal on any generated board", reveals > 0)
    }

    @Test
    fun `every generated board has exactly one solution, the recorded one`() {
        for (board in generatedBoards()) assertUniqueSolution(board.id, puzzleOf(board.givens, board.solution))
    }

    @Test
    fun `corpus files hold 454 real records and 2000 generated records, all 6x6`() {
        val real = realBoards()
        assertEquals((1..454).map { "li:$it" }, real.map { it.id })
        assertEquals((1..454).toList(), real.map { it.n })
        real.forEach { assertShape(it.id, it.givens, it.solution) }

        val generated = generatedBoards()
        assertEquals((1..2000).map { "rnd:$it" }, generated.map { it.id })
        generated.forEach { assertShape(it.id, it.givens, it.solution) }
    }

    /**
     * Applies each step the way the UI does: a single or reveal places its digit, an elimination adds its
     * strikes to [known], and `lastMove` follows the last placed cell. Every step must be sound and must
     * advance the board; the board must reach [SudokuStep.Solved] within its cells plus 40 iterations.
     */
    private fun rerun(id: String, puzzle: SudokuPuzzle): Rerun {
        val solution = puzzle.solution
        val tally = Rerun()
        val known = HashMap<Int, Int>()
        var board = SudokuBoard.of(puzzle)
        var lastMove: Int? = null

        fun place(cell: Int, digit: Int) {
            assertEquals("$id: cell $cell is already filled", 0, board.digitAt(cell))
            assertEquals("$id: cell $cell gets a digit other than the solution's", solution[cell], digit)
            board = board.with(cell, SudokuCell.Entry(digit))
            lastMove = cell
            tally.placements++
        }

        for (iteration in 0 until puzzle.geometry.cellCount + 40) {
            when (val step = SudokuStepper.next(puzzle, board, lastMove, known)) {
                SudokuStep.Solved -> return tally
                is SudokuStep.Mistake -> throw AssertionError("$id: mistake at ${step.cell} on a board fed only sound steps")
                is SudokuStep.Single -> place(step.place.cell, step.place.digit)
                is SudokuStep.Reveal -> {
                    place(step.cell, step.digit)
                    tally.reveals++
                }
                is SudokuStep.Elimination -> {
                    assertTrue(
                        "$id: iteration $iteration repeats a shown elimination",
                        step.struck.any { (cell, mask) -> mask and known.getOrDefault(cell, 0).inv() != 0 },
                    )
                    for ((cell, mask) in step.struck) {
                        assertEquals("$id: elimination on filled cell $cell", 0, board.digitAt(cell))
                        assertEquals(
                            "$id: elimination strikes the solution digit at $cell",
                            0,
                            mask and (1 shl (solution[cell] - 1)),
                        )
                        known[cell] = known.getOrDefault(cell, 0) or mask
                    }
                    tally.eliminations++
                }
            }
        }
        throw AssertionError("$id: no Solved within ${puzzle.geometry.cellCount + 40} iterations")
    }

    private fun assertUniqueSolution(id: String, puzzle: SudokuPuzzle) {
        val result = SudokuSolver.solve(puzzle.geometry, puzzle.givens, limit = 2)
        assertEquals("$id: solution count", 1, result.count)
        assertEquals("$id: solution", puzzle.solution, result.solution)
    }

    private fun assertShape(id: String, givens: String, solution: String) {
        assertEquals("$id: givens length", 36, givens.length)
        assertEquals("$id: solution length", 36, solution.length)
        assertTrue("$id: solution digits", solution.all { it in '1'..'6' })
    }

    private fun puzzleOf(givens: String, solution: String): SudokuPuzzle =
        SudokuPuzzle(SudokuVariants.MINI, KnownSolutions.grid(givens), KnownSolutions.grid(solution))

    private fun realBoards(): List<RealBoard> =
        Json.decodeFromString<List<RealBoard>>(KnownSolutions.fixture("sudoku-boards.json"))

    private fun generatedBoards(): List<GeneratedBoard> =
        Json.decodeFromString<List<GeneratedBoard>>(KnownSolutions.fixture("sudoku-generated.json"))
}
