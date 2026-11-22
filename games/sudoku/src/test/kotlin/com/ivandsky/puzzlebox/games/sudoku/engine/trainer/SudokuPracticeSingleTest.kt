package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuPracticeSingleTest {

    private val starts = SudokuTrainerFixtures.starts

    /** Whether [digit] on open cell [idx] is a single, read from the notes: its cell's only note, or no other cell of one of its units holds it. */
    private fun SudokuTrainerFixtures.PracticeStart.isSingle(idx: Int, digit: Int): Boolean {
        val candidates = SudokuLadder.candidates(puzzle, board)
        val bit = 1 shl (digit - 1)
        if (candidates[idx] and bit == 0) return false
        val geometry = puzzle.geometry
        return candidates[idx] == bit || geometry.units.filter { idx in geometry.cellsOf(it) }.any { unit ->
            geometry.cellsOf(unit).none { it != idx && candidates[it] and bit != 0 }
        }
    }

    private fun SudokuTrainerFixtures.PracticeStart.placements() =
        board.cells.indices.filter { board.digitAt(it) == 0 }.map { SudokuTrainerMove.Place(it, puzzle.solution[it]) }

    @Test
    fun `a valid single the easiest rung does not make is taken as Fits, on a focus board and on a review board`() {
        val fitsOn = mutableSetOf<BoardKind>()
        for (start in starts) {
            val rung = checkNotNull(SudokuLadder.easiest(start.puzzle, start.board))
            val made = rung.steps.filterIsInstance<SudokuStep.Single>().map { it.place.cell to it.place.digit }.toSet()
            for (move in start.placements().filter { start.isSingle(it.idx, it.digit) && (it.idx to it.digit) !in made }) {
                assertEquals("${start.name} ${start.technique.value} $move", SudokuPracticeJudgement.Fits(start.technique), SudokuPractice.judge(start.puzzle, start.board, move))
                fitsOn += start.kind
            }
        }
        assertEquals(BoardKind.entries.toSet(), fitsOn)
    }

    @Test
    fun `a placement that fits but is no single is still Easier`() {
        var easier = 0
        for (start in starts) {
            val rung = checkNotNull(SudokuLadder.easiest(start.puzzle, start.board))
            for (move in start.placements().filter { !start.isSingle(it.idx, it.digit) }) {
                assertEquals("${start.name} ${start.technique.value} $move", SudokuPracticeJudgement.Easier(start.technique, rung.steps.first()), SudokuPractice.judge(start.puzzle, start.board, move))
                easier++
            }
        }
        assertTrue("some placement is no single", easier > 0)
    }

    @Test
    fun `a single the easiest rung does make is still Right`() {
        for (start in starts) {
            val rung = checkNotNull(SudokuLadder.easiest(start.puzzle, start.board))
            for (step in rung.steps.filterIsInstance<SudokuStep.Single>()) {
                val judged = SudokuPractice.judge(start.puzzle, start.board, SudokuTrainerMove.Place(step.place.cell, step.place.digit))
                assertTrue("${start.name} ${start.technique.value}: $judged", judged is SudokuPracticeJudgement.Right)
            }
        }
    }
}
