package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The tall screen (h3400dp) fits the whole catalog in one capture; both images are
// written straight to the ticket evidence directory.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w440dp-h3400dp-420dpi")
class CatalogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `catalog renders the full token set in light`() {
        capture(darkTheme = false, name = "catalog-light.png")
    }

    @Test
    fun `catalog renders the full token set in dark`() {
        capture(darkTheme = true, name = "catalog-dark.png")
    }

    // A17: 150% font scale (Android's "Large" accessibility step) — exactly where fixed
    // heights and adaptive weights regress silently.
    @Test
    fun `catalog renders the full token set in light at font scale 1_5`() {
        capture(darkTheme = false, name = "catalog-light-f1.5.png", fontScale = 1.5f)
    }

    @Test
    fun `catalog renders the full token set in dark at font scale 1_5`() {
        capture(darkTheme = true, name = "catalog-dark-f1.5.png", fontScale = 1.5f)
    }

    private fun capture(darkTheme: Boolean, name: String, fontScale: Float = 1f) {
        // Must run before setContent: LocalDensity reads the configuration at
        // composition. Robolectric's qualifier strings don't parse a font-scale
        // segment, so the programmatic API is the supported mechanism.
        RuntimeEnvironment.setFontScale(fontScale)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    TokenCatalogScreen(Modifier.padding(top = Spacing.xl))
                }
            }
        }
        composeRule.onRoot()
            .captureRoboImage(File(SNAPSHOT_DIR, name).absolutePath)
    }

    companion object {
        // Committed inside the module (NOT gitignored .scratch/) so a fresh clone can
        // verify against goldens instead of having to re-record them.
        private val SNAPSHOT_DIR = File(System.getProperty("user.dir")).resolve("snapshots")
    }
}
