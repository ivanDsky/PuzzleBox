package com.ivandsky.puzzlebox.gameui

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Spec A11: a lesson's controller plays the lesson's puzzle and its win only moves the tutorial on. */
class TutorialSandboxTest {

    private val game = LessonGame(lessons = 3)
    private val session = TutorialSession(game.tutorial)
    private val sandbox = TutorialSandbox(game, session)
    private val scope = TestScope()

    private fun current() = session.state.value as TutorialState.Playing

    @Test
    fun `the controller holds the lesson's own puzzle`() {
        session.advance()

        val controller = sandbox.controllerFor(current(), scope) as LessonGame.Controller

        assertEquals(2, controller.lesson)
    }

    @Test
    fun `winning the lesson moves to the next one`() {
        val controller = sandbox.controllerFor(current(), scope) as LessonGame.Controller

        controller.solve()

        assertEquals(1, current().index)
    }

    @Test
    fun `a late win from an earlier lesson never skips the current one`() {
        val first = sandbox.controllerFor(current(), scope) as LessonGame.Controller
        session.advance() // lesson 1 is already behind us

        first.solve()

        assertEquals(1, current().index)
    }

    @Test
    fun `a lesson the game cannot play fails loudly`() {
        game.rejectWith = "Not a puzzle."

        assertThrows(IllegalStateException::class.java) { sandbox.controllerFor(current(), scope) }
    }
}
