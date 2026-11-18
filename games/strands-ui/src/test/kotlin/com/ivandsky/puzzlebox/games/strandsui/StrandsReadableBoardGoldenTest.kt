package com.ivandsky.puzzlebox.games.strandsui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The readable Strands board (nav-rethink B3), light and dark: [StrandsBoardReadablePreview] at a
 * phone's cell pitch (60 dp, six letters across 360 dp). Letters in ink, a found word, the found
 * spangram, a hinted word and three selected letters in their rings. These are the renders to hold against the approved token sheets.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsReadableBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(dark: Boolean) {
        composeRule.setContent {
            // The preview's theme follows the configuration's night bit, as on a device.
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.testTag("pane"), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.padding(12.dp).width(240.dp)) { StrandsBoardReadablePreview() }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/readable-board-${if (dark) "dark" else "light"}.png")
            .canonicalFile
        composeRule.onNodeWithTag("pane").captureRoboImage(out.absolutePath)
    }

    @Test fun `readable board light`() = capture(dark = false)

    @Test fun `readable board dark`() = capture(dark = true)
}
