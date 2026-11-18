package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How to play with RulesSheet on, over the daily's [GameViewModel] and a friend's puzzle's [CustomGameViewModel]: the
 * rules sheet opens over the live board in place of the tutorial, with the clock folded under it. Got it hands the
 * board and its clock back; Replay the tutorial opens the lessons, the clock still folded.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RulesSheetRouteTest(custom: Boolean) : GameTutorialFixture(custom, playFeel = true, rulesSheet = true) {

    private fun startSeen() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))
    }

    private fun sheetShown() = composeRule.onAllNodesWithTag("rules.sheet").fetchSemanticsNodes().isNotEmpty()

    private fun tapHowToPlay() {
        click("play.how_to_play")
        composeRule.waitUntil { sheetShown() }
    }

    private fun tapInSheet(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    @Test
    fun `how to play shows the rules over the live board and folds its clock, not the tutorial`() {
        startSeen()
        val controller = fakes[alpha].latest
        composeRule.runOnIdle { controller.move() }
        composeRule.runOnIdle { controller.advance(5_000L) }

        tapHowToPlay()

        composeRule.onNodeWithText("Fill the grid").assertExists()
        composeRule.onNodeWithTag("fake.board").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        assertNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(10_000L) }
        assertEquals(5_000L, controller.displayedElapsedMs())
    }

    @Test
    fun `Got it closes the sheet and the clock continues from where it stopped`() {
        startSeen()
        val controller = fakes[alpha].latest
        composeRule.runOnIdle { controller.advance(5_000L) }
        tapHowToPlay()

        tapInSheet("rules.got_it")
        composeRule.waitUntil { !sheetShown() }

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        assertNotNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(1_000L) }
        assertEquals(6_000L, controller.displayedElapsedMs())
    }

    @Test
    fun `Replay the tutorial opens the lessons with the clock still folded`() {
        startSeen()
        val controller = fakes[alpha].latest
        composeRule.runOnIdle { controller.advance(5_000L) }
        tapHowToPlay()

        tapInSheet("rules.replay")
        composeRule.waitUntil { !sheetShown() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(10_000L) }
        assertEquals(5_000L, controller.displayedElapsedMs())
        // Folded once by the sheet; neither the replay nor the lessons banked it again.
        assertEquals(1, controller.folds)
    }

    @Test
    fun `a window that reopens under the sheet is folded again`() {
        startSeen()
        val controller = fakes[alpha].latest
        composeRule.runOnIdle { controller.advance(5_000L) }
        tapHowToPlay()

        // The app came back to the foreground: the game's own lifecycle effect reopens its window.
        composeRule.runOnIdle { controller.onForeground() }
        composeRule.waitForIdle()

        assertNull(controller.clockWindow)
        assertEquals(5_000L, controller.displayedElapsedMs())
    }

    @Test
    fun `a recomposed entry keeps the sheet open`() {
        startSeen()
        tapHowToPlay()

        composeRule.runOnIdle { entry.intValue++ }
        composeRule.waitForIdle()

        assertTrue(sheetShown())
    }

    @Test
    fun `a game with no rules copy opens the tutorial as before`() {
        rulesCopy = null
        startSeen()

        click("play.how_to_play")

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertTrue(!sheetShown())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun entries(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
