package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a practice board is done (spec §2.2, [BoardKind]): what it asks for until then, and that it takes nothing after. */
class SudokuPracticeDoneTest {

    private val order = SudokuTechniqueIds.all

    /** Plays Right moves until the board is Done; returns the ladder index of the rung each was asked on. */
    private fun SudokuPracticeRig.playToDone(): List<Int> {
        val asked = ArrayList<Int>()
        var moves = 0
        while (status !is PracticeStatus.Done) {
            val move = checkNotNull(rightMove()) { "$label: no Right move after $moves" }
            asked += order.indexOf(checkNotNull(judge(move).rung) { "$label: a move with no technique" })
            play(move)
            check(++moves < 500) { "$label did not finish" }
        }
        return asked
    }

    /** The index in the ladder of the rung Show me would play on the board now; null when the board is full or no rung applies. */
    private fun SudokuPracticeRig.nextRung(): Int? = SudokuPractice.showMe(puzzle, board)?.rung?.let { order.indexOf(it) }

    @Test
    fun `a focus board asks only for the trained technique, and is done on another one with the puzzle unsolved`() {
        var unsolved = 0
        val rigs = SudokuPracticeRig.all(BoardKind.Focus)
        assertTrue(rigs.isNotEmpty())
        for (rig in rigs) {
            val asked = rig.playToDone()

            assertTrue(rig.label, asked.isNotEmpty())
            assertEquals(rig.label, setOf(order.indexOf(rig.technique)), asked.toSet())
            assertNotNull(rig.label, (rig.status as PracticeStatus.Done).last)
            if (rig.state.session.isComplete) continue
            unsolved++
            assertTrue("${rig.label}: the next rung is another technique", rig.nextRung() != order.indexOf(rig.technique))
        }
        assertTrue("some focus board ends before the puzzle does", unsolved > 0)
    }

    @Test
    fun `a review board goes on through learned techniques and is done on a later one, the end, or once nothing trained is left`() {
        var later = 0
        var learnedAsked = 0
        val rigs = SudokuPracticeRig.all(BoardKind.Review)
        assertTrue(rigs.isNotEmpty())
        for (rig in rigs) {
            val trained = order.indexOf(rig.technique)
            val asked = rig.playToDone()

            assertTrue(rig.label, asked.all { it <= trained })
            assertTrue(rig.label, trained in asked)
            learnedAsked += asked.count { it < trained }
            if (rig.state.session.isComplete) continue
            later++
            assertTrue(
                "${rig.label}: the next rung is later than the trained one, none applies, or no trained step lies ahead",
                rig.nextRung().let { it == null || it > trained } || !SudokuPractice.trainedAhead(rig.puzzle, rig.board, rig.technique),
            )
        }
        assertTrue("a review board asks an earlier technique", learnedAsked > 0)
        assertTrue("some review board ends on a later technique", later > 0)
    }

    @Test
    fun `a board no technique applies to is Done where it stalls, for both kinds, with the puzzle unsolved`() {
        val rigs = SudokuPracticeRig.all().filter { it.name == "rnd:517" && it.technique.value == "naked-pair" }
        assertEquals(BoardKind.entries.toSet(), rigs.map { it.kind }.toSet())
        for (rig in rigs) {
            rig.playToDone()

            assertFalse(rig.label, rig.state.session.isComplete)
            assertNull("${rig.label}: nothing applies", rig.nextRung())
            assertNotNull(rig.label, (rig.status as PracticeStatus.Done).last)
        }
    }

    @Test
    fun `a Done board refuses digits, notes and Show me, and reports nothing`() {
        var refused = 0
        for (rig in SudokuPracticeRig.all()) {
            rig.playToDone()
            val done = rig.status
            val board = rig.board
            val history = rig.state.session.history
            val verdicts = rig.verdicts.toList()
            val right = rig.rightMove()

            if (!rig.board.isFull) rig.play(rig.wrongDigit())
            if (right != null) rig.play(right)
            rig.practice.showMe()
            rig.practice.gotIt()

            assertEquals(rig.label, board, rig.board)
            assertEquals(rig.label, history, rig.state.session.history)
            assertEquals(rig.label, done, rig.status)
            assertEquals(rig.label, verdicts, rig.verdicts)
            if (right != null) refused++
        }
        assertTrue("some board is done with a Right move still open", refused > 0)
    }

    @Test
    fun `Show me then Got it on the step that ends the board gives Done with nothing to flash`() {
        for (rig in SudokuPracticeRig.all()) {
            var turns = 0
            while (true) {
                rig.practice.showMe()
                assertTrue("${rig.label}: Show me explains first", rig.status is PracticeStatus.Shown)
                rig.practice.gotIt()
                if (rig.status is PracticeStatus.Done) break
                assertEquals(rig.label, PracticeStatus.FindNextMove, rig.status)
                check(++turns < 500) { "${rig.label} did not finish" }
            }

            val done = PracticeStatus.Done(null, needsOtherTechnique = !rig.state.session.isComplete)
            assertEquals(rig.label, done, rig.status)
            rig.practice.showMe()
            assertEquals(rig.label, done, rig.status)
        }
    }

    @Test
    fun `a Right move that ends the board is Done straight away, flashing that step`() {
        for (rig in SudokuPracticeRig.all()) {
            var stepStart = rig.board
            var step: SudokuStep? = null
            while (rig.status !is PracticeStatus.Done) {
                val move = checkNotNull(rig.rightMove())
                if (rig.status !is PracticeStatus.KeepGoing) {
                    stepStart = rig.board
                    step = (rig.judge(move) as SudokuPracticeJudgement.Right).step
                }
                rig.play(move)
                val status = rig.status
                if (status is PracticeStatus.Done) {
                    val expected = SudokuSpotlightMapper.map(checkNotNull(step), rig.puzzle, stepStart, playId = 0)
                    val flash = checkNotNull(status.last)
                    assertEquals(rig.label, expected.roles, flash.roles)
                    assertEquals(rig.label, expected.callout, flash.callout)
                }
            }
        }
    }

    @Test
    fun `a board opens on the trained technique's step, not Done`() {
        for (rig in SudokuPracticeRig.all()) {
            assertFalse(rig.label, rig.status is PracticeStatus.Done)
            assertEquals(rig.label, order.indexOf(rig.technique), rig.nextRung())
        }
    }
}
