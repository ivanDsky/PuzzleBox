package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.ready
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.secondSolved
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.solved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recap story's moves: a tap or a swipe steps, nothing moves alone, back steps back until the first card (A7). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecapStoryTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val pages = mutableListOf<Int>()
    private var closed = 0

    private fun show(state: RecapUiState = ready) = rule.setContent {
        PuzzleBoxTheme {
            RecapScreen(state, "November 2026", gameName, onPage = { pages += it }, onWatchReplay = {}, onClose = { closed++ })
        }
    }

    private fun tapPager(fraction: Float) = rule.onNodeWithTag("recap.pager").performTouchInput {
        click(Offset(width * fraction, height / 2f))
    }

    private fun assertCard(position: Int) =
        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card $position of 2")

    @Test
    fun `opens on the first card with its total`() {
        show()

        assertCard(1)
        rule.onNodeWithContentDescription("42 puzzles solved").assertIsDisplayed()
        rule.onNodeWithTag("recap.solved.game.zip").assertIsDisplayed()
        assertEquals(listOf(0), pages)
    }

    @Test
    fun `a tap on the right steps to the next card`() {
        show()

        tapPager(0.8f)
        rule.waitForIdle()

        assertCard(2)
        rule.onNodeWithContentDescription("7 puzzles solved").assertIsDisplayed()
        assertEquals(listOf(0, 1), pages)
    }

    @Test
    fun `a swipe left steps to the next card and a swipe right steps back`() {
        show()

        rule.onNodeWithTag("recap.pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertCard(2)

        rule.onNodeWithTag("recap.pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertCard(1)
    }

    @Test
    fun `a tap on the left steps back`() {
        show()
        tapPager(0.8f)
        rule.waitForIdle()

        tapPager(0.1f)
        rule.waitForIdle()

        assertCard(1)
    }

    @Test
    fun `a tap on the left of the first card stays put`() {
        show()

        tapPager(0.1f)
        rule.waitForIdle()

        assertCard(1)
        assertEquals(listOf(0), pages)
    }

    @Test
    fun `nothing moves on its own`() {
        show()

        rule.mainClock.advanceTimeBy(60_000L)
        rule.waitForIdle()

        assertCard(1)
        assertEquals(listOf(0), pages)
    }

    @Test
    fun `a press held 280ms hides the chrome until it lets go and is no tap`() {
        show(readyOf(solved, secondSolved, solved).copy(initialPage = 1))
        rule.mainClock.autoAdvance = false
        val pager = rule.onNodeWithTag("recap.pager")

        pager.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(279, ignoreFrameDuration = true)
        rule.onNodeWithTag("recap.close").assertExists()
        rule.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("recap.close").assertDoesNotExist()

        pager.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("recap.close").assertExists()
        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 3")
    }

    @Test
    fun `a fresh open deals the first card face-down and turns it face-up`() {
        rule.mainClock.autoAdvance = false
        show()

        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithContentDescription("42 puzzles solved").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("42 puzzles solved").assertIsDisplayed()
    }

    @Test
    fun `a step throws the top card off and a step back brings it back over the stack`() {
        show()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false

        tapPager(0.8f)
        rule.mainClock.advanceTimeBy(RecapMotion.DeckStackPromote + FrameMs)
        rule.onNodeWithContentDescription("42 puzzles solved").assertDoesNotExist()
        rule.onNodeWithContentDescription("7 puzzles solved").assertIsDisplayed()

        tapPager(0.1f)
        rule.mainClock.advanceTimeBy(RecapMotion.ThrowBackDuration + FrameMs)
        rule.onNodeWithContentDescription("42 puzzles solved").assertIsDisplayed()
        rule.onNodeWithContentDescription("7 puzzles solved").assertDoesNotExist()
    }

    @Test
    fun `a drag short of the commit follows the finger and springs back without stepping`() {
        show()
        val total = rule.onNodeWithContentDescription("42 puzzles solved")
        val rest = total.getBoundsInRoot().left

        rule.onNodeWithTag("recap.pager").performTouchInput {
            down(center)
            repeat(10) {
                advanceEventTime(FrameMs)
                moveBy(Offset(-width * 0.015f, 0f))
            }
        }
        assertTrue("the card follows the finger", total.getBoundsInRoot().left < rest - 20.dp)
        rule.onNodeWithTag("recap.pager").performTouchInput {
            // Still before letting go, so the release is no fling.
            advanceEventTime(200)
            up()
        }
        rule.waitForIdle()

        assertCard(1)
        assertEquals(rest.value, total.getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `with reduced motion the deck opens face-up and a tap swaps cards in one 120ms fade`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.mainClock.autoAdvance = false
        show()

        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithContentDescription("42 puzzles solved").assertExists()

        tapPager(0.8f)
        rule.mainClock.advanceTimeBy(MotionTokens.ReducedFadeDuration + FrameMs)
        rule.onNodeWithContentDescription("42 puzzles solved").assertDoesNotExist()
        rule.onNodeWithContentDescription("7 puzzles solved").assertExists()
    }

    @Test
    fun `the replay button plays the card again where it is`() {
        show()

        rule.onNodeWithTag("recap.replay").assertContentDescriptionEquals("Replay card").performClick()
        rule.waitForIdle()

        assertCard(1)
        rule.onNodeWithContentDescription("42 puzzles solved").assertIsDisplayed()
        assertEquals(listOf(0), pages)
    }

    @Test
    fun `the last card does not step past itself`() {
        show()
        tapPager(0.8f)
        rule.waitForIdle()

        tapPager(0.8f)
        rule.waitForIdle()

        assertCard(2)
    }

    @Test
    fun `system back is left to the screen on the first card and steps back from a later one`() {
        show()
        val dispatcher = rule.activity.onBackPressedDispatcher
        assertFalse("first card: back falls through to close the recap", dispatcher.hasEnabledCallbacks())

        tapPager(0.8f)
        rule.waitForIdle()
        assertTrue("later card: back steps back a card", dispatcher.hasEnabledCallbacks())

        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        assertCard(1)
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `the close button closes the recap`() {
        show()

        rule.onNodeWithTag("recap.close").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun `restoring says the history is coming back and still closes`() {
        show(RecapUiState.Restoring)

        rule.onNodeWithTag("recap.restoring").assertIsDisplayed()
        rule.onNodeWithTag("recap.pager").assertDoesNotExist()
        rule.onNodeWithTag("recap.close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `an empty period says so and still closes`() {
        show(RecapUiState.Empty)

        rule.onNodeWithTag("recap.empty").assertIsDisplayed()
        rule.onNodeWithTag("recap.close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `a phone shows no fact list`() {
        show()

        rule.onNodeWithTag("recap.facts").assertDoesNotExist()
        rule.onNodeWithTag("recap.pager").assertExists()
    }

    @Test
    fun `a story reopens on its saved page`() {
        show(ready.copy(initialPage = 1))

        assertCard(2)
        rule.onNodeWithContentDescription("7 puzzles solved").assertIsDisplayed()
    }

    @Test
    fun `a rotation keeps the card the player is on instead of the first loaded page`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            PuzzleBoxTheme {
                RecapScreen(readyOf(solved, secondSolved, solved), "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
            }
        }
        repeat(2) {
            tapPager(0.8f)
            rule.waitForIdle()
        }
        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 3 of 3")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 3 of 3")
    }
}

private const val FrameMs = 16L
