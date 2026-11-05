package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Spec A11: the tutorial's step machine — lessons in order, then Ready; Skip ends it. */
class TutorialSessionTest {

    private val steps = LessonGame(lessons = 3).tutorial

    private fun playing(index: Int) = TutorialState.Playing(index, count = 3, step = steps[index])

    @Test
    fun `starts on the first lesson of all of them`() {
        assertEquals(playing(0), TutorialSession(steps).state.value)
    }

    @Test
    fun `each win moves to the next lesson and the last one lands on ready`() {
        val session = TutorialSession(steps)

        session.advance()
        assertEquals(playing(1), session.state.value)
        session.advance()
        assertEquals(playing(2), session.state.value)
        session.advance()
        assertEquals(TutorialState.Ready, session.state.value)
    }

    @Test
    fun `a one-lesson tutorial is ready after one win`() {
        val session = TutorialSession(steps.take(1))

        session.advance()

        assertEquals(TutorialState.Ready, session.state.value)
    }

    @Test
    fun `skip ends the tutorial from any lesson`() {
        val session = TutorialSession(steps)
        session.advance()

        session.skip()

        assertEquals(TutorialState.Skipped, session.state.value)
    }

    @Test
    fun `a finished tutorial stays finished`() {
        val ready = TutorialSession(steps).apply { repeat(3) { advance() } }
        ready.advance()
        ready.skip()
        assertEquals(TutorialState.Ready, ready.state.value)

        val skipped = TutorialSession(steps).apply { skip() }
        skipped.advance()
        skipped.skip()
        assertEquals(TutorialState.Skipped, skipped.state.value)
    }

    @Test
    fun `a tutorial with no lessons is a wiring bug`() {
        assertThrows(IllegalArgumentException::class.java) { TutorialSession(emptyList()) }
    }
}
