package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Spec A11: the tutorial's step machine — each lesson solved, then the next, then Ready; Skip ends it. */
class TutorialSessionTest {

    private val steps = LessonGame(lessons = 3).tutorial

    private fun playing(index: Int, solved: Boolean = false) =
        TutorialState.Playing(index, count = 3, step = steps[index], solved = solved)

    @Test
    fun `starts on the first lesson of all of them`() {
        assertEquals(playing(0), TutorialSession(steps).state.value)
    }

    @Test
    fun `a win keeps the same lesson on screen, solved`() {
        val session = TutorialSession(steps)

        session.advance()

        assertEquals(playing(0, solved = true), session.state.value)
    }

    @Test
    fun `next after a solved lesson moves to the next one and the last one lands on ready`() {
        val session = TutorialSession(steps)

        session.advance()
        session.next()
        assertEquals(playing(1), session.state.value)
        session.advance()
        session.next()
        assertEquals(playing(2), session.state.value)
        session.advance()
        session.next()
        assertEquals(TutorialState.Ready, session.state.value)
    }

    @Test
    fun `next before the lesson is solved does nothing`() {
        val session = TutorialSession(steps)

        session.next()

        assertEquals(playing(0), session.state.value)
    }

    @Test
    fun `a second win on a solved lesson does nothing`() {
        val session = TutorialSession(steps)
        session.advance()

        session.advance()

        assertEquals(playing(0, solved = true), session.state.value)
        session.next()
        assertEquals(playing(1), session.state.value)
    }

    @Test
    fun `a one-lesson tutorial is ready after one win and its beat`() {
        val session = TutorialSession(steps.take(1))

        session.advance()
        session.next()

        assertEquals(TutorialState.Ready, session.state.value)
    }

    @Test
    fun `skip ends the tutorial from any lesson`() {
        val session = TutorialSession(steps)
        session.advance()
        session.next()

        session.skip()

        assertEquals(TutorialState.Skipped, session.state.value)
    }

    @Test
    fun `skip ends the tutorial during a solved lesson's beat`() {
        val session = TutorialSession(steps)
        session.advance()

        session.skip()
        session.next()

        assertEquals(TutorialState.Skipped, session.state.value)
    }

    @Test
    fun `a finished tutorial stays finished`() {
        val ready = TutorialSession(steps).apply {
            repeat(3) {
                advance()
                next()
            }
        }
        ready.advance()
        ready.next()
        ready.skip()
        assertEquals(TutorialState.Ready, ready.state.value)

        val skipped = TutorialSession(steps).apply { skip() }
        skipped.advance()
        skipped.next()
        skipped.skip()
        assertEquals(TutorialState.Skipped, skipped.state.value)
    }

    @Test
    fun `a tutorial with no lessons is a wiring bug`() {
        assertThrows(IllegalArgumentException::class.java) { TutorialSession(emptyList()) }
    }
}
