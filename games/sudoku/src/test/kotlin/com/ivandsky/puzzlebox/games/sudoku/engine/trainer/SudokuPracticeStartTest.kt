package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SudokuPracticeStartTest {

    private val starts = SudokuTrainerFixtures.starts
    private val singles = SudokuTechniqueIds.all.take(4).toSet()

    @Test
    fun `the fixtures start practice on the singles they use and on every elimination`() {
        val reached = starts.map { it.technique.value }.toSet()
        assertTrue(reached.toString(), reached.containsAll(listOf("last-cell", "hidden-single-box", "pointing", "claiming", "naked-pair", "hidden-pair")))
    }

    @Test
    fun `a start board is the walk's board, with the trained technique the easiest rung`() {
        for (start in starts) {
            val label = "${start.name} ${start.technique.value}"
            assertEquals(label, start.technique, SudokuLadder.easiest(start.puzzle, start.board)?.id)
            start.board.cells.forEachIndexed { idx, cell ->
                val given = start.puzzle.givens[idx]
                val solution = start.puzzle.solution[idx]
                when {
                    given != 0 -> assertEquals("$label cell $idx", SudokuCell.Given(given), cell)
                    cell is SudokuCell.Entry -> assertEquals("$label cell $idx", solution, cell.digit)
                    cell is SudokuCell.Notes -> assertTrue("$label cell $idx keeps its solution digit", cell.mask and (1 shl (solution - 1)) != 0)
                    else -> fail("$label: open cell $idx shows $cell")
                }
            }
        }
    }

    @Test
    fun `every open cell shows its candidates as notes, less what the walk's eliminations struck`() {
        var struckAny = false
        for (start in starts) {
            val candidates = SudokuCandidates.of(start.puzzle, start.board)
            val label = "${start.name} ${start.technique.value}"
            start.board.cells.forEachIndexed { idx, cell ->
                if (cell !is SudokuCell.Notes) return@forEachIndexed
                assertEquals("$label cell $idx notes are candidates", cell.mask, cell.mask and candidates[idx])
                if (cell.mask != candidates[idx]) {
                    struckAny = true
                    // Only an elimination walked before this rung strikes a note.
                    assertTrue("$label: a single's start holds a strike at cell $idx", start.technique !in singles)
                }
            }
        }
        assertTrue("some later start carries a strike", struckAny)
    }

    @Test
    fun `the same puzzle starts on the same board`() {
        for (start in starts) assertEquals(start.board, SudokuPractice.start(start.puzzle, start.technique))
    }

    @Test
    fun `a technique the puzzle never makes the easiest rung has no start`() {
        val daily = SudokuTrainerFixtures.dailies.getValue("li:1")
        assertNull(SudokuPractice.start(daily, TechniqueId("hidden-pair")))
        assertNull(SudokuPractice.start(daily, TechniqueId("no-such-technique")))
    }

    @Test
    fun `a stored solution that is not the puzzle's unique solution has no start`() {
        val puzzle = SudokuTrainerFixtures.dailies.getValue("li:1")
        val other = puzzle.copy(solution = puzzle.solution.map { it % puzzle.geometry.size + 1 })
        assertTrue(SudokuPractice.start(puzzle, SudokuTechniqueIds.lastCell) != null)
        assertNull(SudokuPractice.start(other, SudokuTechniqueIds.lastCell))
    }
}
