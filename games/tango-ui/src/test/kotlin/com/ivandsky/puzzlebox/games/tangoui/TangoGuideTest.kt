package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.render.TangoViolations
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.TutorialSandbox
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.gameui.TutorialState
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec A12, guided lessons: Tango's lesson 1 teaches input before any rule, so the board
 * ghosts each open square's answer and never shows rule feedback, whatever the tap order.
 * Everything runs through the real controller and the board's own tap cycle.
 */
class TangoGuideTest {

    private val lesson1 = TangoTutorial.steps.first()

    /** Lesson 1's answer, read off the solved board in [TangoTutorial]'s comment. */
    private val answer = mapOf(4 to TangoCell.MOON, 6 to TangoCell.SUN, 8 to TangoCell.SUN)

    private fun env(won: MutableList<CompletionRecord>) =
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = { won += it })

    /** A controller holding [step]'s board, guided the way the tutorial sandbox guides it. */
    private fun lesson(step: TutorialStep, won: MutableList<CompletionRecord> = mutableListOf()) =
        TangoPlayController(env(won), hintMode = HintMode.Stepwise(Dispatchers.Unconfined)).also {
            assertEquals(Adopt.Adopted, it.adoptToday(step.puzzle, step.ref))
            if (step.guided) it.guide()
        }

    /** Taps [idx] until it holds [value] (a sun is one tap, a moon two). */
    private fun TangoPlayController.tapTo(idx: Int, value: TangoCell) {
        repeat(if (value == TangoCell.SUN) 1 else 2) { onTap(idx) }
    }

    private fun TangoPlayController.assertNoRuleFeedback(at: String) {
        assertNull(at, violation)
        assertNull(at, lastRejection)
        assertEquals(at, 0, session?.mistakeCount)
        val model = rendererModel()!!
        assertTrue(at, TangoViolations.of(model).isEmpty())
        for (idx in model.cells.indices) assertNull(at, TangoSemantics.violationPhraseAt(model, idx))
    }

    @Test
    fun `only lesson 1 is guided`() {
        assertEquals(listOf(true, false, false, false), TangoTutorial.steps.map { it.guided })
    }

    @Test
    fun `the guide works out every open square's answer`() {
        assertEquals(answer, lesson(lesson1).guidance.answer)
    }

    @Test
    fun `a sun on every open square shows no caption and no violation while guided`() {
        val unguided = TangoPlayController(env(mutableListOf()), hintMode = HintMode.Stepwise(Dispatchers.Unconfined)).apply {
            adoptToday(lesson1.puzzle, lesson1.ref)
            answer.keys.forEach { onTap(it) }
        }
        // The same taps on an unguided board break a rule, so the guided silence is real.
        assertNotNull(unguided.violation)

        val s = lesson(lesson1)
        for (idx in answer.keys) {
            s.onTap(idx)
            s.assertNoRuleFeedback("sun on $idx")
        }
        assertFalse(s.isComplete)
    }

    @Test
    fun `any tap order that reaches the answer wins`() {
        val squares = answer.keys.toList()
        val orders = squares.flatMap { a ->
            squares.filter { it != a }.flatMap { b -> squares.filter { it != a && it != b }.map { c -> listOf(a, b, c) } }
        }
        assertEquals(6, orders.size)
        for (order in orders) {
            val won = mutableListOf<CompletionRecord>()
            val s = lesson(lesson1, won)
            for (idx in order) {
                s.tapTo(idx, answer.getValue(idx))
                s.assertNoRuleFeedback("order $order, square $idx")
            }
            assertTrue("order $order", s.isComplete)
            assertEquals(lesson1.ref, won.single().puzzleRef)
        }

        // A wandering order wins too: every square a sun first, then the moon square again.
        val won = mutableListOf<CompletionRecord>()
        val s = lesson(lesson1, won)
        squares.forEach { s.onTap(it) }
        s.onTap(answer.filterValues { it == TangoCell.MOON }.keys.single())
        assertTrue(s.isComplete)
        assertEquals(1, won.size)
    }

    @Test
    fun `a ghost stays on an open square until it matches`() {
        val s = lesson(lesson1)
        assertEquals(answer, s.rendererModel()!!.ghosts)
        val moon = answer.filterValues { it == TangoCell.MOON }.keys.single()

        s.onTap(moon) // a sun, not yet the answer
        val model = s.rendererModel()!!
        assertEquals(TangoCell.MOON, model.ghosts[moon])
        assertTrue(TangoSemantics.cellStateDescription(model, moon).endsWith(TangoSemantics.ghostPhrase(TangoCell.MOON)))

        s.onTap(moon) // the moon: its ghost goes, the others stay
        val matched = s.rendererModel()!!
        assertNull(matched.ghosts[moon])
        assertFalse(TangoSemantics.cellStateDescription(matched, moon).contains(TangoSemantics.ghostPhrase(TangoCell.MOON)))
        assertEquals(answer - moon, matched.ghosts)

        (answer - moon).forEach { (idx, value) -> s.tapTo(idx, value) }
        assertTrue(s.rendererModel()!!.ghosts.isEmpty())
    }

    @Test
    fun `an unguided daily never shows ghosts`() {
        val daily = TangoPlayController(env(mutableListOf()), hintMode = HintMode.Stepwise(Dispatchers.Unconfined)).also {
            assertEquals(
                Adopt.Adopted,
                it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(LocalDate.of(2026, 11, 4))),
            )
        }
        assertNull(daily.guidance.answer)
        for ((idx, value) in TangoPlayFixture.placements) {
            assertTrue(daily.rendererModel()!!.ghosts.isEmpty())
            daily.onPlacement(TangoPlacement(idx, value))
        }
        assertTrue(daily.isComplete)
        assertTrue(daily.rendererModel()!!.ghosts.isEmpty())
    }

    @Test
    fun `the sandbox guides lesson 1 and plays through to ready`() {
        val session = TutorialSession(TangoTutorial.steps)
        val sandbox = TutorialSandbox(TangoPlayUi(HintMode.Stepwise(UnconfinedTestDispatcher())), session)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val lessonTaps = listOf(answer.keys.toList(), listOf(6), listOf(1, 8), listOf(15))

        for ((index, taps) in lessonTaps.withIndex()) {
            val playing = session.state.value as TutorialState.Playing
            assertEquals(index, playing.index)
            val s = sandbox.controllerFor(playing, scope) as TangoPlayController
            assertEquals(playing.step.guided, s.guidance.active)
            if (s.guidance.active) {
                taps.forEach { s.onTap(it) } // all suns first: no feedback, no win yet
                s.assertNoRuleFeedback("lesson ${index + 1}")
                s.onTap(answer.filterValues { it == TangoCell.MOON }.keys.single())
            } else {
                taps.forEach { s.onTap(it) }
                assertTrue(s.rendererModel()!!.ghosts.isEmpty())
            }
            assertTrue((session.state.value as TutorialState.Playing).solved)
            session.next()
        }
        assertEquals(TutorialState.Ready, session.state.value)
    }
}
