package com.ivandsky.puzzlebox.app.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Screenshot suite for the new debug-only Showkase browser (showkase-v2-05, A5) — the
// pattern (runner, qualifiers, font-scale handling, committed goldens) is the deleted
// CatalogScreenshotTest/ComponentScreenshotTest's, relocated to where the browser lives:
// the composables are :app-debug-only, so the tests sit in the debug unit-test source
// set and exercise the REAL entry point (metadata via the generated provider, prefs via
// Robolectric's fresh per-test app storage → groups start collapsed).
//
// application = plain Application: Robolectric would otherwise boot the real
// PuzzleBoxApplication (merged manifest), whose onCreate starts Koin on the JVM-global
// context and races DiGraphTest's own startKoin. The browser reads no DI graph.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w440dp-h3400dp-420dpi", application = android.app.Application::class)
class ShowkaseBrowserScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `browser home renders in light`() {
        capture(name = "browser-home-light.png")
    }

    @Test
    fun `browser home renders in dark`() {
        capture(darkTheme = true, name = "browser-home-dark.png")
    }

    // A17-style accessibility step carried over from the old catalog suite: font scale
    // is exactly where the header and group rows regress silently.
    @Test
    fun `browser home renders in light at font scale 1_5`() {
        capture(name = "browser-home-light-f1.5.png", fontScale = 1.5f)
    }

    @Test
    fun `browser home renders in dark at font scale 1_5`() {
        capture(darkTheme = true, name = "browser-home-dark-f1.5.png", fontScale = 1.5f)
    }

    // Expansion + inline previews on the checkerboard backdrop (ticket 02), driven
    // through the real toggle so the golden covers the interaction's end state.
    @Test
    fun `expanded group shows inline previews`() {
        capture(name = "browser-group-expanded.png") { expandButtons() }
    }

    // Row→detail (ticket 04): light+dark panes and the variant chip row for a real
    // indexed component.
    @Test
    fun `component row opens the detail screen`() {
        capture(name = "browser-detail.png") {
            expandButtons()
            composeRule.onNodeWithText("PrimaryButton").performClick()
            composeRule.waitForIdle()
        }
    }

    private fun expandButtons() {
        // CollapsibleGroup renders the group name uppercased; the whole header row is
        // clickable and merges the text node, so clicking the text hits the toggle.
        composeRule.onNodeWithText("BUTTONS").performClick()
        composeRule.waitForIdle()
    }

    private fun capture(darkTheme: Boolean = false, name: String, fontScale: Float = 1f, interact: () -> Unit = {}) {
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
                    ShowkaseBrowserScreen()
                }
            }
        }
        interact()
        composeRule.onRoot()
            .captureRoboImage(File(SNAPSHOT_DIR, name).absolutePath)
    }

    companion object {
        // Committed inside the module (NOT gitignored .scratch/) so a fresh clone can
        // verify against goldens instead of having to re-record them.
        private val SNAPSHOT_DIR = File(System.getProperty("user.dir")).resolve("snapshots")
    }
}
