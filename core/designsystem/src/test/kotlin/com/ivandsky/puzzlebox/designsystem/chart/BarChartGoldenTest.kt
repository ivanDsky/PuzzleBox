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
 * Goldens of the flat [BarChart]: a full week, a few bars, one bar and equal bars, light and dark, plus the week
 * mirrored for a right-to-left layout.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class BarChartGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val week = previewWeekdayTimes(95, 88, 102, 76, 71, 64, 58)
    private val what = "Average solve time by weekday"

    @Test
    fun `week light`() = capture("BarChart_week.png", dark = false) { BarChart(week, what, PreviewTime) }

    @Test
    fun `week dark`() = capture("BarChart_week_dark.png", dark = true) { BarChart(week, what, PreviewTime) }

    @Test
    fun `week right to left`() = capture("BarChart_week_rtl.png", dark = false, rtl = true) { BarChart(week, what, PreviewTime) }

    @Test
    fun `a few bars`() = capture("BarChart_few.png", dark = false) { BarChart(previewWeekdayTimes(95, 60, 120), what, PreviewTime) }

    @Test
    fun `one bar`() = capture("BarChart_one.png", dark = false) { BarChart(previewWeekdayTimes(95), what, PreviewTime) }

    @Test
    fun `equal bars fill the plot`() = capture("BarChart_equal.png", dark = true) { BarChart(previewWeekdayTimes(45, 45, 45, 45), what, PreviewTime) }

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
