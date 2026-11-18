package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of [UnderlineTabs], light and dark: each image stacks the strip once per selected
 * position (the indicator under each tab). Three tabs and four tabs, with and without counts,
 * the four with the Duels badge; and, at 320dp and font scale 1.3, the same with counts.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabsGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `three tabs without counts`() = capture("UnderlineTabs_three_plain", dark = false) { ThreePlainTabsSample() }

    @Test
    fun `three tabs without counts in the dark theme`() = capture("UnderlineTabs_three_plain_dark", dark = true) { ThreePlainTabsSample() }

    @Test
    fun `three tabs with counts`() = capture("UnderlineTabs_three_counts", dark = false) { ThreeCountsTabsSample() }

    @Test
    fun `three tabs with counts in the dark theme`() = capture("UnderlineTabs_three_counts_dark", dark = true) { ThreeCountsTabsSample() }

    @Test
    fun `four tabs without counts`() = capture("UnderlineTabs_four_plain", dark = false) { FourPlainTabsSample() }

    @Test
    fun `four tabs without counts in the dark theme`() = capture("UnderlineTabs_four_plain_dark", dark = true) { FourPlainTabsSample() }

    @Test
    fun `four tabs with counts and the Duels badge`() = capture("UnderlineTabs_four_badge", dark = false) { FourBadgeTabsSample() }

    @Test
    fun `four tabs with counts and the Duels badge in the dark theme`() = capture("UnderlineTabs_four_badge_dark", dark = true) { FourBadgeTabsSample() }

    @Test
    fun `three tabs with counts at 320dp and font scale 1 point 3`() = capture("UnderlineTabs_three_counts_320_font13", dark = false, widthDp = 320, fontScale = 1.3f) { ThreeCountsTabsSample() }

    @Test
    fun `three tabs with counts at 320dp and font scale 1 point 3 in the dark theme`() = capture("UnderlineTabs_three_counts_320_font13_dark", dark = true, widthDp = 320, fontScale = 1.3f) { ThreeCountsTabsSample() }

    @Test
    fun `four tabs with the badge at 320dp and font scale 1 point 3`() = capture("UnderlineTabs_four_badge_320_font13", dark = false, widthDp = 320, fontScale = 1.3f) { FourBadgeTabsSample() }

    @Test
    fun `four tabs with the badge at 320dp and font scale 1 point 3 in the dark theme`() = capture("UnderlineTabs_four_badge_320_font13_dark", dark = true, widthDp = 320, fontScale = 1.3f) { FourBadgeTabsSample() }

    private fun capture(name: String, dark: Boolean, widthDp: Int = 360, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Box(Modifier.width(widthDp.dp).background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$name.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
