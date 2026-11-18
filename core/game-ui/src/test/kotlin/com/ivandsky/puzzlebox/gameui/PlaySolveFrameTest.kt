package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A6: the solve moment plays once per solve, before the hand-off, and rests the board for [CelebrationMotion.SolveMomentMs]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlaySolveFrameTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var solved by mutableStateOf<Instant?>(null)
    private var handOffs = 0
    private val buzzes = ArrayList<HapticFeedbackType>()

    /** The buzz goes through [LocalHapticFeedback], where the Vibration switch gates it; never the view or the vibrator. */
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalHapticFeedback provides haptics,
            ) {
                PuzzleBoxTheme(darkTheme = false) {
                    PlaySolveFrame(brand = GameBrand.Neutral, solved = solved, onHandOff = { handOffs++ }) {
                        Box(Modifier.size(200.dp))
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(16)
    }

    /** With the clock held, a test-thread write reaches the composition only once it is applied. */
    private fun solve(identity: Instant?) {
        solved = identity
        Snapshot.sendApplyNotifications()
    }


    @Test
    fun `a solve rests the board for the solve moment, the stamp lands with the pop, then hands off once`() {
        setFrame()
        composeRule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()

        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(CelebrationMotion.BoardPopStartMs - 100L)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("play.solve_stamp").assertExists()

        composeRule.mainClock.advanceTimeBy(CelebrationMotion.SolveMomentMs - CelebrationMotion.BoardPopStartMs - 300)
        assertEquals("still resting", 0, handOffs)
        composeRule.onNodeWithTag("play.solve_stamp").assertExists()
        composeRule.onNodeWithTag("play.solve_rest").assertExists()

        composeRule.mainClock.advanceTimeBy(300)
        assertEquals(1, handOffs)
        composeRule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("one solve, one hand-off", 1, handOffs)

        // Play again: a fresh board, then a new solve plays its own moment.
        solve(null)
        composeRule.mainClock.advanceTimeBy(5_000)
        solve(Instant.ofEpochSecond(2))
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("play.solve_stamp").assertExists()
        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals(2, handOffs)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
    }

    @Test
    fun `a board already solved when it first draws hands off at once`() {
        solved = Instant.ofEpochSecond(1)
        setFrame()
        assertEquals(1, handOffs)
        composeRule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
    }

    @Test
    fun `a tap on the board during the rest hands off at once, once`() {
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("play.solve_rest").performClick()
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals(1, handOffs)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("the tap and the timer end it once between them", 1, handOffs)
    }

    @Test
    fun `a tap on the stamp's area hands off at once, once`() {
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.onNodeWithTag("play.solve_stamp").assertExists().performClick()
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals("the stamp passed the tap to the layer under it", 1, handOffs)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals(1, handOffs)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
    }

    @Test
    fun `a tap before the stamp lands hands off with no stamp after`() {
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("play.solve_rest").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, handOffs)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
    }

    @Test
    fun `reduced motion keeps the outline and the same rest, with no stamp`() {
        setAnimatorScale(0f)
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(CelebrationMotion.SolveMomentMs - 100)
        assertEquals("the rest is a pause, so reduced motion keeps it", 0, handOffs)
        composeRule.onNodeWithTag("play.solve_rest").assertExists()
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, handOffs)
    }

    @Test
    fun `the success buzz comes once as the solve moment starts`() {
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals("at the sweep start, not at the hand-off", listOf(HapticFeedbackType.Confirm), buzzes)
        assertEquals(0, handOffs)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("once, not again at the hand-off", 1, buzzes.size)
    }

    @Test
    fun `reduced motion keeps the success buzz`() {
        setAnimatorScale(0f)
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
    }

    @Test
    fun `the solve confirms once and hands off`() {
        setFrame()
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
        assertEquals(1, handOffs)
    }

    @Test
    fun `a restored solved board hands off without a confirm`() {
        solved = Instant.ofEpochSecond(1)
        setFrame()
        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
        assertEquals(1, handOffs)
    }
}
