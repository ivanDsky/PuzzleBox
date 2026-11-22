package com.ivandsky.puzzlebox.insights.stats.grouped

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M08: with the system's animations off the arrival is one fade, in place. Run on the test clock, a block reads the
 * same bounds at every instant (no rise, no scale) while its opacity climbs; with animations on, the control block rises.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsArrivalReducedTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(animationScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animationScale)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
                    Box(Modifier.size(100.dp).statsArrival(index = 0, key = "block").background(Color.Black).testTag("block"))
                }
            }
        }
    }

    private fun greenAt(): Float = rule.onNodeWithTag("block").captureToImage().toPixelMap()[50, 50].green

    @Test
    fun `reduced motion fades in place with no rise and no scale`() {
        show(animationScale = 0f)
        rule.mainClock.advanceTimeBy(16)
        val atStart = rule.onNodeWithTag("block").getBoundsInRoot()
        val early = greenAt()
        rule.mainClock.advanceTimeBy(56)
        val mid = greenAt()
        assertEquals(atStart, rule.onNodeWithTag("block").getBoundsInRoot())
        rule.mainClock.advanceTimeBy(400)
        val end = greenAt()
        assertEquals(atStart, rule.onNodeWithTag("block").getBoundsInRoot())
        // Black on the white-ish surface: the more faded in, the darker (lower green).
        assertTrue("fading in: $early > $mid > $end", early > mid && mid > end)
    }

    @Test
    fun `with animations on the same block rises as it fades`() {
        show(animationScale = 1f)
        rule.mainClock.advanceTimeBy(16)
        val atStart = rule.onNodeWithTag("block").getBoundsInRoot()
        rule.mainClock.advanceTimeBy(600)
        assertNotEquals("the control rises into place", atStart, rule.onNodeWithTag("block").getBoundsInRoot())
    }

    @Test
    fun `a block that has arrived does not arrive again`() {
        val motion = StatsMotion()
        assertTrue(motion.claimArrival("block"))
        assertTrue(!motion.claimArrival("block"))
        assertTrue(motion.claimArrival("other"))
    }
}
