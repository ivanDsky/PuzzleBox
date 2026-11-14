package com.ivandsky.puzzlebox.gameui.replay

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F1, F2, F14 (chrome side): what the player hands every board as [ReplayBoardOptions]. `animate` is "playing and Reduce
 * motion off"; `showErrors` is the page's switch. The boards' own motion is pinned in each game's UI module.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayBoardOptionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)
    private var seen: ReplayBoardOptions? = null

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            seen = options
        }
    }

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show(showErrors: Boolean = true) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { ReplayPlayer(timeline, board, showErrors = showErrors) } }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun seekTo(ms: Long) {
        composeRule.onNodeWithTag(ReplayTags.SEEK).performSemanticsAction(SemanticsActions.SetProgress) { it(ms.toFloat()) }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun options() = checkNotNull(seen) { "the board was never drawn" }

    @Test
    fun `a paused replay rests`() {
        show()

        assertFalse(options().animate)
    }

    @Test
    fun `a playing replay animates`() {
        show()

        tap(ReplayTags.PLAY)

        assertTrue(options().animate)
    }

    @Test
    fun `pausing again rests`() {
        show()
        tap(ReplayTags.PLAY)

        tap(ReplayTags.PLAY)

        assertFalse(options().animate)
    }

    @Test
    fun `a playing replay with Reduce motion on rests`() {
        setAnimatorScale(0f)
        show()

        tap(ReplayTags.PLAY)

        assertFalse(options().animate)
    }

    @Test
    fun `the page's show errors switch reaches the board`() {
        show(showErrors = false)

        assertEquals(false, options().showErrors)
    }

    @Test
    fun `show errors is on by default`() {
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { ReplayPlayer(timeline, board) } }

        assertTrue(options().showErrors)
    }

    @Test
    fun `a forward seek while playing lands without animation, the next frame of play animates again`() {
        show()
        seekTo(0)
        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(100)
        assertTrue("plain play animates", options().animate)

        seekTo(5_000)

        assertFalse("the frame a seek lands on rests", options().animate)
        composeRule.mainClock.advanceTimeBy(100)
        assertTrue("play goes on animating", options().animate)
    }

    @Test
    fun `a backward seek while playing lands without animation`() {
        show()
        seekTo(0)
        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(3_100)

        seekTo(1_000)

        assertFalse(options().animate)
    }

    @Test
    fun `stepping to the next move while playing is not a seek`() {
        show()
        seekTo(0)
        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(100)

        tap(ReplayTags.NEXT)

        // Next pauses, as before: the paused frame rests.
        assertFalse(options().animate)
    }
}
