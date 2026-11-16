package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** L2: the celebration overlay ends after 1 s, 2 s with a moment, or at once on a tap, and only once. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SolveCelebrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var moment by mutableStateOf(false)
    private var dones = 0

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setOverlay() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SolveCelebration(brand = GameBrand.Neutral, moment = moment, onDone = { dones++ })
            }
        }
        composeRule.mainClock.advanceTimeBy(16)
    }

    @Test
    fun `without a moment it ends after one second, not before`() {
        setOverlay()
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals("still playing", 0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("one celebration, one end", 1, dones)
    }

    @Test
    fun `with a moment it runs two seconds`() {
        moment = true
        setOverlay()
        composeRule.mainClock.advanceTimeBy(1_900)
        assertEquals("a moment outlasts the normal length", 0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `a moment signalled during the first second still gets the full two`() {
        setOverlay()
        composeRule.mainClock.advanceTimeBy(500)
        moment = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(1_400)
        assertEquals(0, dones)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }

    @Test
    fun `one tap ends it at once, and the timer does not end it again`() {
        setOverlay()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("solve.celebration").performClick()
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals(1, dones)

        composeRule.mainClock.advanceTimeBy(5_000)
        assertEquals("tap and timer end it once between them", 1, dones)
    }

    @Test
    fun `reduced motion shows the stamp and keeps the same length and tap`() {
        setAnimatorScale(0f)
        setOverlay()
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.onNodeWithTag("solve.celebration_stamp", useUnmergedTree = true).assertExists()
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals(0, dones)
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, dones)
    }
}
