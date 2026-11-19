package com.ivandsky.puzzlebox.designsystem.chart

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the flat [LineChart]: a time line, a share line on a fixed scale, a flat line, a two-point line and a long
 * one, light and dark, plus the time line mirrored for a right-to-left layout.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class LineChartGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val time = previewTimes(95, 88, 102, 76, 71, 64)

    @Test
    fun `time line light`() = capture("LineChart_time.png", dark = false) { LineChart(time, "Average solve time", PreviewTime) }

    @Test
    fun `time line dark`() = capture("LineChart_time_dark.png", dark = true) { LineChart(time, "Average solve time", PreviewTime) }

    @Test
    fun `time line right to left`() = capture("LineChart_time_rtl.png", dark = false, rtl = true) { LineChart(time, "Average solve time", PreviewTime) }

    @Test
    fun `share line on a fixed scale`() = capture("LineChart_share.png", dark = false) {
        LineChart(previewShares(40, 55, 50, 80), "Hint-free solves", PreviewShare, valueRange = 0.0..1.0)
    }

    @Test
    fun `share line on a fixed scale dark`() = capture("LineChart_share_dark.png", dark = true) {
        LineChart(previewShares(40, 55, 50, 80), "Hint-free solves", PreviewShare, valueRange = 0.0..1.0)
    }

    @Test
    fun `all equal values draw a flat line`() = capture("LineChart_flat.png", dark = false) {
        LineChart(previewTimes(45, 45, 45), "Average solve time", PreviewTime)
    }

    @Test
    fun `two points are the shortest line`() = capture("LineChart_two.png", dark = false) {
        LineChart(previewTimes(90, 60), "Average solve time", PreviewTime)
    }

    @Test
    fun `a long line keeps only its latest dot`() = capture("LineChart_long.png", dark = true) {
        LineChart(previewTimes(*IntArray(40) { 80 + ((it * 7) % 23) - it / 3 }), "Average solve time", PreviewTime)
    }

    private fun capture(file: String, dark: Boolean, rtl: Boolean = false, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$file")
                .canonicalFile
                .absolutePath,
        )
    }
}
