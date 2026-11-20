package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An Easier move or Show me stands as an explanation until Got it. The keypad sits outside the grid, which the
 * Spotlight layer covers, so the board itself must refuse what the keypad sends meanwhile.
 */
class SudokuPracticeExplanationTest {

    private fun SudokuPracticeRig.rightMove(): SudokuTrainerMove? =
        moves().firstOrNull { judge(it) is SudokuPracticeJudgement.Right }

    @Test
    fun `keypad input is refused while Show me's explanation stands, and Show me does nothing`() {
        for (rig in SudokuPracticeRig.all()) {
            rig.practice.showMe()
            val shown = rig.status
            val board = rig.board
            val verdicts = rig.verdicts.toList()

            rig.play(rig.rightMove() ?: continue)
            rig.play(rig.wrongDigit())
            rig.practice.showMe()

            assertEquals(rig.label, board, rig.board)
            assertEquals(rig.label, shown, rig.status)
            assertEquals(rig.label, verdicts, rig.verdicts)
        }
    }

    @Test
    fun `keypad input is refused while an Easier explanation stands, and is not a Wrong`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val easier = rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>() ?: continue
            rig.play(easier)
            val standing = rig.status as PracticeStatus.Easier
            val board = rig.board
            val verdicts = rig.verdicts.toList()

            rig.play(rig.wrongDigit())
            rig.play(easier)

            assertEquals(rig.label, board, rig.board)
            assertEquals(rig.label, standing, rig.status)
            assertEquals(rig.label, verdicts, rig.verdicts)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `Got it ends Show me's explanation, the status is FindNextMove, and the board takes moves again`() {
        for (rig in SudokuPracticeRig.all()) {
            rig.practice.showMe()
            assertTrue(rig.label, rig.status is PracticeStatus.Shown || rig.status == PracticeStatus.Solved)
            if (rig.status == PracticeStatus.Solved) continue

            rig.practice.gotIt()
            assertEquals(rig.label, PracticeStatus.FindNextMove, rig.status)

            val move = rig.rightMove() ?: continue
            val before = rig.board
            rig.play(move)
            assertTrue(rig.label, rig.board != before)
            assertTrue(rig.label, rig.status is PracticeStatus.Right || rig.status is PracticeStatus.KeepGoing || rig.status == PracticeStatus.Solved)
        }
    }

    @Test
    fun `Got it ends an Easier explanation without reporting, and the same move is then judged afresh`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val easier = rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>() ?: continue
            rig.play(easier)
            rig.practice.gotIt()
            assertEquals(rig.label, PracticeStatus.FindNextMove, rig.status)

            rig.play(easier)
            assertTrue(rig.label, rig.status is PracticeStatus.Easier)
            assertEquals(rig.label, listOf(TrainerVerdict.Wrong, TrainerVerdict.Wrong), rig.verdicts)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `Got it does nothing when no explanation stands`() {
        for (rig in SudokuPracticeRig.all()) {
            rig.practice.gotIt()
            assertEquals(rig.label, PracticeStatus.FindNextMove, rig.status)

            rig.play(rig.wrongDigit())
            val wrong = rig.status
            rig.practice.gotIt()
            assertEquals(rig.label, wrong, rig.status)

            rig.play(rig.rightMove() ?: continue)
            val answered = rig.status
            rig.practice.gotIt()
            assertEquals(rig.label, answered, rig.status)
        }
    }
}
