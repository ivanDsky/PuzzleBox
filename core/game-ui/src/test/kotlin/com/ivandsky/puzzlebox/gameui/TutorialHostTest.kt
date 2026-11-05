package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private companion object {
        /** Well inside the success beat, and well past it. */
        const val MID_BEAT_MS = 400L
        const val PAST_THE_BEAT_MS = 1_000L

        /** Either side of the hold's end, wider than a frame. */
        const val HOLD_MARGIN_MS = 100L
    }

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

    /** Wins the current lesson and waits out its success beat. */
    private fun solveLesson() {
        composeRule.onNodeWithTag("lesson.solve").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
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
    fun `a win holds the same board with a success line, then the next lesson follows`() {
        setHost()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("lesson.solve").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("tutorial.success").assertExists()
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 1")
        composeRule.onNodeWithContentDescription("Step 1 of 3").assertExists()
        assertEquals(1, game.controllers.size)

        composeRule.mainClock.advanceTimeBy(MID_BEAT_MS)
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 1")

        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
        composeRule.onNodeWithTag("tutorial.success").assertDoesNotExist()
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 2")
        composeRule.onNodeWithTag("tutorial.instruction").assertTextEquals("Lesson 2: tap Solve.")
        assertEquals(2, game.controllers.size)
    }

    @Test
    fun `the success beat holds for the same length as before`() {
        setHost()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("lesson.solve").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MotionTokens.SolveHoldMillis - HOLD_MARGIN_MS)
        composeRule.onNodeWithTag("tutorial.success").assertExists()
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 1")

        composeRule.mainClock.advanceTimeBy(2 * HOLD_MARGIN_MS)
        composeRule.onNodeWithTag("tutorial.success").assertDoesNotExist()
        composeRule.onNodeWithTag("lesson.board").assertTextEquals("Board 2")
    }

    @Test
    fun `each cleared lesson says a different word from the one before`() {
        setHost()
        composeRule.mainClock.autoAdvance = false

        val words = List(3) {
            composeRule.onNodeWithTag("lesson.solve").performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(MID_BEAT_MS)
            val word = composeRule.onNodeWithTag("tutorial.success_word", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
            composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
            word
        }

        words.zipWithNext().forEach { (before, after) -> assertNotEquals(before, after) }
    }

    @Test
    fun `skip during the success beat ends the tutorial at once`() {
        setHost()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("lesson.solve").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("tutorial.skip").performClick()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)

        assertEquals(1, finished)
        assertEquals(TutorialState.Skipped, session.state.value)
        composeRule.onNodeWithTag("lesson.board").assertDoesNotExist()
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

        composeRule.onNodeWithTag("tutorial.ready").assertExists()
        composeRule.onNodeWithTag("lesson.board").assertDoesNotExist()
        assertEquals(0, finished)

        composeRule.onNodeWithTag("tutorial.finish").performClick()
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

        composeRule.onNodeWithTag("tutorial.ready").assertExists()
        assertTrue(dailySessions.isEmpty())
        assertTrue(dailyRecords.isEmpty())
        assertFalse(daily.isComplete)
        // The lessons really did win: each one published and emitted its record, into the sandbox.
        val lessons = game.controllers - daily
        assertEquals(listOf(1, 2, 3), lessons.map { it.lesson })
        assertTrue(lessons.all { it.published.size == 1 && it.won.size == 1 })
    }
}
