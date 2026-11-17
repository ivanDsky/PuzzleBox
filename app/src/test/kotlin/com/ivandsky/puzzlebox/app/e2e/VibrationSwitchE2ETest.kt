package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Vibration switch at the real shell's root: a win's solve buzz reaches the platform only while it is on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class VibrationSwitchE2ETest {

    private val platform = ArrayList<HapticFeedbackType>()
    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            platform += hapticFeedbackType
        }
    }

    private fun winZip(vibration: Boolean, playFeel: Boolean = false) {
        val graph = if (playFeel) shellGraph(ReleaseFlag.ResultMoments, ReleaseFlag.PlayFeel) else shellGraph(ReleaseFlag.ResultMoments)
        runBlocking { graph.preferences.setVibration(vibration) }
        shellRobot(tabAt(PlayKey), graph = graph, platformHaptics = recorder) {
            val win = with(DailySolve.Zip) { playToTheLastMove() }
            rule.runOnIdle(win)
            repeat(40) { rule.mainClock.advanceTimeByFrame() }
            rule.waitForIdle()
        }
    }

    @Test
    fun `with the switch on a win buzzes the platform`() {
        winZip(vibration = true)
        assertTrue("the win played no haptic at all: $platform", platform.isNotEmpty())
    }

    @Test
    fun `with the switch off a win reaches the platform with nothing`() {
        winZip(vibration = false)
        assertEquals(emptyList<HapticFeedbackType>(), platform)
    }

    @Test
    fun `with PlayFeel on and the switch on the moves and the win buzz the platform`() {
        winZip(vibration = true, playFeel = true)
        // The control: the same run as the off case below, so its silence is the gate and not a dead harness.
        assertTrue("PlayFeel's moves and win played no haptic at all: $platform", platform.isNotEmpty())
    }

    @Test
    fun `with PlayFeel on and the switch off the moves and the win reach the platform with nothing`() {
        winZip(vibration = false, playFeel = true)
        assertEquals(emptyList<HapticFeedbackType>(), platform)
    }
}
