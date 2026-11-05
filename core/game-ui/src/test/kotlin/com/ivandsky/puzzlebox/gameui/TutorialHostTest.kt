package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spec A11: the tutorial plays the game's own board one lesson at a time, Skip is always
 * there, and a lesson never reaches the daily record or session sinks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TutorialHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = LessonGame(lessons = 3)
    private val session = TutorialSession(game.tutorial)
    private var finished = 0

    private fun setHost() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TutorialHost(ui = game, session = session, onFinished = { finished++ })
            }
        }
    }

    private fun solveLesson() {
        composeRule.onNodeWithTag("lesson.solve").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the first lesson shows its step, its instruction and the game's board`() {
        setHost()

        composeRule.onNodeWithContentDescription("Step 1 of 3").assertExists()
        composeRule.onNodeWithTag("tutorial.instruction").assertTextEquals("Lesson 1: tap Solve.")
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 1")
        composeRule.onNodeWithTag("tutorial.skip").assertExists()
    }

    @Test
    fun `solving a lesson moves on to the next one`() {
        setHost()

        solveLesson()

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()
        composeRule.onNodeWithTag("tutorial.instruction").assertTextEquals("Lesson 2: tap Solve.")
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 2")
        assertEquals(0, finished)
    }

    @Test
    fun `skip ends the tutorial and hands back once`() {
        setHost()
        solveLesson()

        composeRule.onNodeWithTag("tutorial.skip").performClick()
        composeRule.waitForIdle()

        assertEquals(1, finished)
        assertEquals(TutorialState.Skipped, session.state.value)
        composeRule.onNodeWithTag("lesson.board").assertDoesNotExist()
    }

    @Test
    fun `after the last lesson a calm ready screen hands back`() {
        setHost()
        repeat(3) { solveLesson() }

        composeRule.onNodeWithText("You're ready!").assertExists()
        composeRule.onNodeWithTag("lesson.board").assertDoesNotExist()
        assertEquals(0, finished)

        composeRule.onNodeWithText("Let's play").performClick()
        composeRule.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun `a lesson's caption comes from the game's own feedback`() {
        setHost()

        composeRule.runOnIdle { game.controllers.last().violation = "Two suns side by side." }

        composeRule.onNodeWithText("Two suns side by side.").assertExists()
    }

    @Test
    fun `the tutorial never touches the daily record or session sinks`() {
        // The daily controller, wired the way the play store wires it.
        val dailySessions = mutableListOf<ByteArray>()
        val dailyRecords = mutableListOf<CompletionRecord>()
        val daily = game.createController(
            PlayEnvironment(TestScope(), onSessionChanged = { dailySessions += it }, onWon = { dailyRecords += it }),
        ) as LessonGame.Controller
        daily.adoptToday(game.tutorial.first().puzzle, game.tutorial.first().ref)
        setHost()

        repeat(3) { solveLesson() }

        composeRule.onNodeWithText("You're ready!").assertExists()
        assertTrue(dailySessions.isEmpty())
        assertTrue(dailyRecords.isEmpty())
        assertFalse(daily.isComplete)
        // The lessons really did win: each one published and emitted its record, into the sandbox.
        val lessons = game.controllers - daily
        assertEquals(listOf(1, 2, 3), lessons.map { it.lesson })
        assertTrue(lessons.all { it.published.size == 1 && it.won.size == 1 })
    }
}
