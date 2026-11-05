package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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

/** A6: the solve moment plays once per solve, before the hand-off; reduced motion shows only the outline. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlaySolveFrameTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var solved by mutableStateOf<Instant?>(null)
    private var handOffs = 0

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlaySolveFrame(brand = GameBrand.Neutral, solved = solved, onHandOff = { handOffs++ }) {
                    Box(Modifier.size(200.dp))
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
    fun `a solve plays the moment once, then hands off`() {
        setFrame()
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()

        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals("the hand-off waits for the moment", 0, handOffs)

        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithTag("play.solve_stamp").assertExists()
        assertEquals(1, handOffs)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("one solve, one hand-off", 1, handOffs)

        // Play again: a fresh board, then a new solve plays its own moment.
        solve(null)
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
        solve(Instant.ofEpochSecond(2))
        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals(2, handOffs)
    }

    @Test
    fun `a board already solved when it first draws hands off at once`() {
        solved = Instant.ofEpochSecond(1)
        setFrame()
        assertEquals(1, handOffs)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
    }

    @Test
    fun `reduced motion shows only the outline, then hands off`() {
        setAnimatorScale(0f)
        setFrame()

        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithTag("play.solve_stamp").assertDoesNotExist()
        assertEquals(1, handOffs)
    }
}
