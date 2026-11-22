package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fields of the shared practice statuses a Mini Sudoku board fills in: the unit, the other technique, the attempt, the stop. */
class SudokuPracticeStatusTest {

    /** A rig played on, by Show me, to a board where the easiest rung is no longer the trained technique; null if it never is. */
    private fun SudokuPracticeRig.pastTechnique(): SudokuPracticeRig? {
        while (status !is PracticeStatus.Done && SudokuPractice.showMe(puzzle, board)?.let { it.rung == technique } == true) {
            practice.showMe()
            practice.gotIt()
        }
        return takeIf { status !is PracticeStatus.Done && SudokuPractice.showMe(puzzle, board) != null }
    }

    private fun SudokuStep.Elimination.notes(size: Int): List<SudokuTrainerMove.RemoveNote> =
        struck.flatMap { (cell, mask) -> (1..size).filter { mask and (1 shl (it - 1)) != 0 }.map { SudokuTrainerMove.RemoveNote(cell, it) } }

    @Test
    fun `a step that has notes left to take off counts them as notes`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val step = SudokuPractice.showMe(rig.puzzle, rig.board)?.step as? SudokuStep.Elimination ?: continue
            val notes = step.notes(rig.puzzle.geometry.size)
            if (notes.size < 2) continue
            rig.play(notes.first())
            assertEquals(rig.label, PracticeStatus.KeepGoing(notes.size - 1, trained = true, unit = "note"), rig.status)
            played++
        }
        assertTrue("a pair or a pointing strikes several notes", played > 0)
    }

    @Test
    fun `a Right step of the trained technique names no other technique`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.find<SudokuPracticeJudgement.Right, SudokuTrainerMove.Place>() ?: continue
            rig.play(move)
            val status = rig.status as? PracticeStatus.Right ?: continue
            assertNull(rig.label, status.technique)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `a Right step of an easier technique names that technique by its list name`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val past = rig.pastTechnique() ?: continue
            val move = past.moves().firstOrNull { past.judge(it) is SudokuPracticeJudgement.Right } ?: continue
            val rung = checkNotNull(past.judge(move).rung)
            past.play(move)
            val status = past.status as? PracticeStatus.Right ?: continue
            assertEquals(past.label, SudokuTrainerCatalog.name(rung), status.technique)
            played++
        }
        assertTrue("some practice goes on to another rung with a one-square step", played > 0)
    }

    @Test
    fun `every undone wrong move is its own status, so a repeat reaches the tally`() {
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.wrongDigit()
            rig.play(move)
            val first = rig.status as PracticeStatus.Wrong
            rig.play(move)
            val second = rig.status as PracticeStatus.Wrong

            assertEquals(rig.label, first.why, second.why)
            assertNotEquals(rig.label, first, second)
            assertEquals(rig.label, 1, first.attempt)
            assertEquals(rig.label, 2, second.attempt)
        }
    }

    @Test
    fun `a board that ends with squares left says the next move needs another technique, a solved one does not`() {
        var unsolved = 0
        var solved = 0
        for (rig in SudokuPracticeRig.all()) {
            while (rig.status !is PracticeStatus.Done) {
                rig.play(checkNotNull(rig.rightMove()) { "${rig.label}: no Right move" })
            }
            val done = rig.status as PracticeStatus.Done
            assertEquals(rig.label, !rig.state.session.isComplete, done.needsOtherTechnique)
            if (done.needsOtherTechnique) unsolved++ else solved++
        }
        assertTrue("some board ends before the puzzle does", unsolved > 0)
        assertTrue("some board ends solved", solved > 0)
    }

    @Test
    fun `Show me then Got it on the ending step says the same`() {
        var unsolved = 0
        for (rig in SudokuPracticeRig.all(BoardKind.Focus)) {
            while (rig.status !is PracticeStatus.Done) {
                rig.practice.showMe()
                rig.practice.gotIt()
            }
            val done = rig.status as PracticeStatus.Done
            assertEquals(rig.label, !rig.state.session.isComplete, done.needsOtherTechnique)
            if (done.needsOtherTechnique) unsolved++
        }
        assertTrue("some focus board ends before the puzzle does", unsolved > 0)
    }
}
