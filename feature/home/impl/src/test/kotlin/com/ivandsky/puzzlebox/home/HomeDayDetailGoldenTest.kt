package com.ivandsky.puzzlebox.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tablet day pane's four states, light and dark. Goldens live in `feature/home/impl/snapshots/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeDayDetailGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun `all played light`() = capture("all-played", dark = false) { HomeDayAllPlayedPreview() }
    @Test fun `all played dark`() = capture("all-played", dark = true) { HomeDayAllPlayedPreview() }
    @Test fun `missed some light`() = capture("missed-some", dark = false) { HomeDayMissedSomePreview() }
    @Test fun `missed some dark`() = capture("missed-some", dark = true) { HomeDayMissedSomePreview() }
    @Test fun `signed out light`() = capture("signed-out", dark = false) { HomeDaySignedOutPreview() }
    @Test fun `signed out dark`() = capture("signed-out", dark = true) { HomeDaySignedOutPreview() }
    @Test fun `longest values light`() = capture("longest-values", dark = false) { HomeDayLongestValuesPreview() }
    @Test fun `longest values dark`() = capture("longest-values", dark = true) { HomeDayLongestValuesPreview() }

    private fun capture(state: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            // The previews' PuzzleBoxTheme follows the system theme, so dark scenes set the night uiMode.
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, content = content)
        }
        composeRule.waitForIdle()
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/home-day-$state-tablet1280-${if (dark) "dark" else "light"}.png")
            .canonicalFile
        out.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(out.absolutePath)
    }
}
