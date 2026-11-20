package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
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

/** What a practice board reports to the trainer's count (spec §2.2, A5), and Show me. */
class SudokuPracticeCountingTest {

    /** A rig played on, by Show me, to a board where the easiest rung is no longer the trained technique; null if it never is. */
    private fun SudokuPracticeRig.pastTechnique(): SudokuPracticeRig? {
        while (SudokuPractice.showMe(puzzle, board)?.let { it.rung == technique } == true) practice.showMe()
        return takeIf { SudokuPractice.showMe(puzzle, board) != null }?.also { verdicts.clear() }
    }

    private fun SudokuStep.Elimination.squares(size: Int): List<SudokuTrainerMove.RemoveNote> =
        struck.flatMap { (cell, mask) -> (1..size).filter { mask and (1 shl (it - 1)) != 0 }.map { SudokuTrainerMove.RemoveNote(cell, it) } }

    @Test
    fun `a trained Right is reported once`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.find<SudokuPracticeJudgement.Right, SudokuTrainerMove.Place>() ?: continue
            rig.play(move)
            assertEquals(rig.label, listOf(TrainerVerdict.Right), rig.verdicts)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `a step of several notes is Right square by square and reported once, on its first square`() {
        var stepsPlayed = 0
        for (rig in SudokuPracticeRig.all()) {
            val step = SudokuPractice.showMe(rig.puzzle, rig.board)?.step as? SudokuStep.Elimination ?: continue
            val squares = step.squares(rig.puzzle.geometry.size)
            if (squares.size < 2) continue

            var spotlight: Any? = null
            squares.forEachIndexed { turn, square ->
                rig.play(square)
                val status = rig.status as PracticeStatus.Right
                assertTrue("${rig.label} square $turn", status.trained)
                spotlight = spotlight ?: status.spotlight
                assertEquals("${rig.label} square $turn keeps the step's Spotlight", spotlight, status.spotlight)
                assertFalse("${rig.label} square $turn stays off", rig.noteOn(square.idx, square.digit))
                assertEquals("${rig.label} after square $turn", listOf(TrainerVerdict.Right), rig.verdicts)
            }
            stepsPlayed++
        }
        assertTrue("a pair or a pointing strikes several notes", stepsPlayed > 0)
    }

    @Test
    fun `an undone move on the trained technique's turn is reported Wrong`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            rig.play(rig.wrongDigit())
            assertEquals(rig.label, listOf(TrainerVerdict.Wrong), rig.verdicts)

            val easier = rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>() ?: continue
            rig.play(easier)
            assertEquals(rig.label, listOf(TrainerVerdict.Wrong, TrainerVerdict.Wrong), rig.verdicts)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `an undone move on another rung reports nothing`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val past = rig.pastTechnique() ?: continue
            past.play(past.wrongDigit())
            past.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>()?.let { past.play(it) }

            assertTrue(past.label, past.status is PracticeStatus.Wrong || past.status is PracticeStatus.Easier)
            assertEquals(past.label, emptyList<TrainerVerdict>(), past.verdicts)
            played++
        }
        assertTrue("some practice goes on to an easier rung", played > 0)
    }

    @Test
    fun `Right on another rung reports nothing and is not trained`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val past = rig.pastTechnique() ?: continue
            val move = past.moves().firstOrNull { past.judge(it) is SudokuPracticeJudgement.Right } ?: continue
            past.play(move)

            assertEquals(past.label, emptyList<TrainerVerdict>(), past.verdicts)
            assertFalse(past.label, (past.status as PracticeStatus.Right).trained)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `Show me makes the whole easiest step and says so`() {
        for (rig in SudokuPracticeRig.all()) {
            val shown = checkNotNull(SudokuPractice.showMe(rig.puzzle, rig.board))
            val before = rig.board
            rig.practice.showMe()

            val status = rig.status as PracticeStatus.Shown
            val expected = SudokuSpotlightMapper.map(shown.step, rig.puzzle, before, playId = 0)
            assertEquals(rig.label, expected.roles, status.spotlight.roles)
            assertEquals(rig.label, expected.callout, status.spotlight.callout)
            assertEquals(rig.label, status.spotlight, rig.practice.shown)
            when (val step = shown.step) {
                is SudokuStep.Single -> assertEquals(rig.label, step.place.digit, rig.board.digitAt(step.place.cell))
                is SudokuStep.Elimination -> step.squares(rig.puzzle.geometry.size).forEach {
                    assertFalse("${rig.label} $it", rig.noteOn(it.idx, it.digit))
                }
                else -> error("${rig.label}: $step")
            }
            assertEquals(rig.label, listOf(TrainerVerdict.ShowMe), rig.verdicts)
        }
    }

    @Test
    fun `Show me on another rung reports nothing`() {
        var played = 0
        for (rig in SudokuPracticeRig.all()) {
            val past = rig.pastTechnique() ?: continue
            past.practice.showMe()

            assertTrue(past.label, past.status is PracticeStatus.Shown)
            assertEquals(past.label, emptyList<TrainerVerdict>(), past.verdicts)
            played++
        }
        assertTrue(played > 0)
    }

    @Test
    fun `Show me to the end solves the board`() {
        for (rig in SudokuPracticeRig.all()) {
            var turns = 0
            while (rig.status != PracticeStatus.Solved) {
                rig.practice.showMe()
                check(++turns < 500) { "${rig.label} did not finish" }
            }
            assertTrue(rig.label, rig.state.session.isComplete)
            assertEquals(rig.label, rig.puzzle.solution, rig.board.cells.map { it.digit })
        }
    }
}
