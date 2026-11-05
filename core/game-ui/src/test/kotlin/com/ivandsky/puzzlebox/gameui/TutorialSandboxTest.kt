package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec A11/A12: a lesson's controller plays the lesson's puzzle, a guided one is guided, and its win only moves the tutorial on. */
class TutorialSandboxTest {

    private val game = LessonGame(lessons = 3, guided = setOf(1))
    private val session = TutorialSession(game.tutorial)
    private val sandbox = TutorialSandbox(game, session)
    private val scope = TestScope()

    private fun current() = session.state.value as TutorialState.Playing

    private fun passLesson() {
        session.advance()
        session.next()
    }

    @Test
    fun `the controller holds the lesson's own puzzle`() {
        passLesson()

        val controller = sandbox.controllerFor(current(), scope) as LessonGame.Controller

        assertEquals(2, controller.lesson)
    }

    @Test
    fun `winning the lesson marks it solved and keeps it on screen`() {
        val controller = sandbox.controllerFor(current(), scope) as LessonGame.Controller

        controller.solve()

        assertEquals(0, current().index)
        assertTrue(current().solved)
    }

    @Test
    fun `a late win from an earlier lesson never touches the current one`() {
        val first = sandbox.controllerFor(current(), scope) as LessonGame.Controller
        passLesson() // lesson 1 is already behind us

        first.solve()

        assertEquals(1, current().index)
        assertEquals(false, current().solved)
    }

    @Test
    fun `a guided lesson asks its controller to guide, after its puzzle is adopted`() {
        val controller = sandbox.controllerFor(current(), scope) as LessonGame.Controller

        assertEquals(listOf(1), controller.guidedLessons)
    }

    @Test
    fun `an unguided lesson is never guided`() {
        passLesson()

        val controller = sandbox.controllerFor(current(), scope) as LessonGame.Controller

        assertTrue(controller.guidedLessons.isEmpty())
    }

    @Test
    fun `a guided lesson whose controller cannot guide fails loudly`() {
        game.canGuide = false

        assertThrows(IllegalStateException::class.java) { sandbox.controllerFor(current(), scope) }
    }

    @Test
    fun `a lesson the game cannot play fails loudly`() {
        game.rejectWith = "Not a puzzle."

        assertThrows(IllegalStateException::class.java) { sandbox.controllerFor(current(), scope) }
    }
}
