package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuPracticeBoardTest {

    private fun SudokuPracticeRig.hint(step: SudokuStep, before: SudokuBoard): Spotlight =
        SudokuSpotlightMapper.map(step, puzzle, before, playId = 0)

    private fun assertSameHint(label: String, expected: Spotlight, actual: Spotlight) {
        assertEquals(label, expected.roles, actual.roles)
        assertEquals(label, expected.callout, actual.callout)
    }

    @Test
    fun `the board opens on the start board with every open square's candidates as notes, waiting for a move`() {
        val rigs = SudokuPracticeRig.all()
        assertTrue(rigs.isNotEmpty())
        for (rig in rigs) {
            assertEquals(rig.label, SudokuPractice.start(rig.puzzle, rig.technique, rig.kind), rig.board)
            assertEquals(rig.label, PracticeStatus.FindNextMove, rig.status)
            assertFalse(rig.label, rig.state.canUndo)
            rig.board.cells.forEachIndexed { idx, cell ->
                if (rig.puzzle.givens[idx] == 0 && cell.digit == 0) assertTrue("${rig.label} square $idx", cell is SudokuCell.Notes)
            }
        }
    }

    @Test
    fun `a digit of an easiest-rung step stays, and the Spotlight is that step's own`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.find<SudokuPracticeJudgement.Right, SudokuTrainerMove.Place>() ?: continue
            val judged = rig.judge(move) as SudokuPracticeJudgement.Right
            val before = rig.board
            rig.play(move)

            assertEquals(rig.label, move.digit, rig.board.digitAt(move.idx))
            val status = rig.status
            if (status is PracticeStatus.Right) assertEquals(rig.label, judged.rung == rig.technique, status.trained)
            assertSameHint(rig.label, rig.hint(judged.step, before), checkNotNull(rig.flash))
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `a note of an easiest-rung step comes off and stays off, on any instance of the rung`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val moves = rig.moves().filterIsInstance<SudokuTrainerMove.RemoveNote>().filter { rig.judge(it) is SudokuPracticeJudgement.Right }
            if (moves.isEmpty()) continue
            for (move in moves) {
                val fresh = rig.fresh()
                val judged = fresh.judge(move) as SudokuPracticeJudgement.Right
                val before = fresh.board
                fresh.play(move)

                assertFalse(fresh.label, fresh.noteOn(move.idx, move.digit))
                when (val status = fresh.status) {
                    is PracticeStatus.Right -> assertSameHint(fresh.label, fresh.hint(judged.step, before), status.spotlight)
                    is PracticeStatus.Done -> assertSameHint(fresh.label, fresh.hint(judged.step, before), checkNotNull(status.last))
                    is PracticeStatus.KeepGoing -> assertTrue(fresh.label, status.left >= 1)
                    else -> error("${fresh.label}: $status")
                }
                played++
            }
        }
        assertTrue("some elimination is played", played > 0)
    }

    @Test
    fun `a move that fits the solution under an easier technique is undone and names that technique`() {
        var digits = 0
        var notes = 0
        for (rig in SudokuPracticeRig.all()) {
            for (move in listOfNotNull(rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>(), rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.RemoveNote>())) {
                val fresh = rig.fresh()
                val judged = fresh.judge(move) as SudokuPracticeJudgement.Easier
                val boardBefore = fresh.board
                val before = fresh.play(move)

                assertEquals(fresh.label, before, fresh.state.session)
                assertEquals(fresh.label, boardBefore, fresh.board)
                assertFalse(fresh.label, fresh.state.canRedo)
                val status = fresh.status as PracticeStatus.Easier
                assertEquals(fresh.label, SudokuTrainerCatalog.name(judged.rung), status.technique)
                assertSameHint(fresh.label, fresh.hint(judged.step, boardBefore), status.spotlight)
                if (move is SudokuTrainerMove.Place) digits++ else notes++
            }
        }
        assertTrue("a digit on an elimination's board", digits > 0)
        assertTrue("a note on a single's board", notes > 0)
    }

    @Test
    fun `a wrong digit and the solution's note taken off are undone with a line why, leaving the exact board`() {
        for (rig in SudokuPracticeRig.all()) {
            for (move in listOf(rig.wrongDigit(), rig.solutionNote())) {
                val fresh = rig.fresh()
                val before = fresh.play(move)

                assertEquals("${fresh.label} $move", before, fresh.state.session)
                assertFalse(fresh.label, fresh.state.canRedo)
                val status = fresh.status as PracticeStatus.Wrong
                assertTrue("${fresh.label} $move: ${status.why}", status.why.isNotBlank())
            }
        }
    }

    @Test
    fun `an undone move leaves no residue, so the next Right move is the board's only mark`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val right = rig.moves().firstOrNull { rig.judge(it) is SudokuPracticeJudgement.Right } ?: continue
            val easier = rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>()
            rig.play(rig.wrongDigit())
            if (easier != null) {
                rig.play(easier)
                rig.practice.gotIt()
            }
            rig.play(right)

            assertEquals(rig.label, 1, rig.state.session.history.done.size)
            assertTrue(rig.label, rig.status is PracticeStatus.Right || rig.status is PracticeStatus.KeepGoing || rig.status is PracticeStatus.Done)
            played++
        }
        assertTrue(played > 0)
    }
}
