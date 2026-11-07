package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec A11/A12: Tango's lessons are legal 4×4 boards with one answer each, the real
 * controller solves each one from its few intended taps, and a wrong answer is named
 * by the very rule its lesson teaches.
 */
class TangoTutorialTest {

    private val steps = TangoTutorial.steps

    /** Each lesson's intended taps, in order (a sun is one tap, a moon two). Lesson 1 is guided, so any order works. */
    private val taps = listOf(
        listOf(4, 4, 6, 8),
        listOf(6),
        listOf(1, 8),
        listOf(15),
    )

    private fun puzzleOf(step: TutorialStep): TangoPuzzle =
        (TangoCodec.decode(step.puzzle.payload) as DecodeResult.Ok).value

    private fun controller(won: MutableList<CompletionRecord> = mutableListOf()) = TangoPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = { won += it }),
    )

    /** A controller that adopted [step]'s board, and guides it if the step is guided, the way the tutorial sandbox does. */
    private fun adopted(step: TutorialStep, won: MutableList<CompletionRecord> = mutableListOf()) =
        controller(won).also {
            assertEquals(Adopt.Adopted, it.adoptToday(step.puzzle, step.ref))
            if (step.guided) it.guide()
        }

    /** One tap on [idx], through the board's own tap cycle. */
    private fun TangoPlayController.tap(idx: Int) {
        TangoTapPolicy.onTap(rendererModel()!!, idx).forEach(this::onPlacement)
    }

    @Test
    fun `the play registration ships four lessons`() {
        assertSame(steps, TangoPlayUi().tutorial)
        assertEquals(4, steps.size)
        assertEquals(steps.size, taps.size)
    }

    @Test
    fun `every lesson is a legal 4x4 board with exactly one answer`() {
        for (step in steps) {
            val puzzle = puzzleOf(step)

            assertEquals(TangoDefinition.id, step.puzzle.gameId)
            assertTrue(TangoDefinition.decode(step.puzzle.payload) is DecodeResult.Ok)
            assertEquals(4, puzzle.n)
            assertTrue(step.instruction, TangoUniqueness.isUniquelySolvable(puzzle))
            assertTrue(step.instruction, puzzle.cellCount - puzzle.givens.size in 1..3)
        }
    }

    @Test
    fun `every lesson plays as a custom board, never a daily`() {
        for (step in steps) {
            assertEquals(TangoDefinition.id, step.ref.gameId)
            assertNull((step.ref.key as PuzzleKey.Daily).puzzleNumber)
        }
    }

    @Test
    fun `each lesson is solved by its intended taps through the real controller`() {
        for ((step, lessonTaps) in steps.zip(taps)) {
            val won = mutableListOf<CompletionRecord>()
            val s = adopted(step, won)

            lessonTaps.dropLast(1).forEach { s.tap(it) }
            assertFalse(step.instruction, s.isComplete)
            s.tap(lessonTaps.last())

            assertTrue(step.instruction, s.isComplete)
            // The win is what moves the tutorial on: one record, under the lesson's own ref.
            assertEquals(step.ref, won.single().puzzleRef)
        }
    }

    @Test
    fun `no intended tap in an unguided lesson ever breaks a rule, not even for a moment`() {
        for ((step, lessonTaps) in steps.zip(taps).filterNot { (step, _) -> step.guided }) {
            val s = adopted(step)

            for ((count, idx) in lessonTaps.withIndex()) {
                s.tap(idx)

                val at = "${step.instruction} (tap ${count + 1})"
                assertNull(at, s.lastRejection)
                assertEquals(at, 0, s.session?.mistakeCount)
                // Nothing new is waiting out the settle window, so the scan below is unsuppressed.
                assertTrue(at, s.suppressedSets.isEmpty())
                assertTrue(at, TangoSemantics.violations(s.rendererModel()!!).isEmpty())
                assertNull(at, s.violation)
            }
        }
    }

    @Test
    fun `a wrong answer is named by the rule its lesson teaches`() {
        fun wrongMoon(lesson: Int, idx: Int): TangoVerdict.Reason? =
            adopted(steps[lesson]).run {
                onPlacement(TangoPlacement(idx, TangoCell.MOON))
                assertFalse(isComplete)
                lastRejection?.reason
            }

        assertEquals(TangoVerdict.Reason.ThreeInARow, wrongMoon(lesson = 1, idx = 6))
        assertEquals(TangoVerdict.Reason.MarkerConflict, wrongMoon(lesson = 2, idx = 1))
        assertEquals(TangoVerdict.Reason.MarkerConflict, wrongMoon(lesson = 2, idx = 8))
        assertEquals(TangoVerdict.Reason.CountExceeded, wrongMoon(lesson = 3, idx = 15))
    }
}
