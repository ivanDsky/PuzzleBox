package com.ivandsky.puzzlebox.app.debug

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File
import org.junit.Assert.assertTrue
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
@Config(sdk = [34], qualifiers = "w440dp-h3400dp-420dpi", application = DrainTestApplication::class)
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

    // Ticket 09: theme/BG controls are radio groups (home row, directly under the
    // search bar). Direct selection replaces the ticket-06/07 cycle chips: one tap on
    // the "Dark" option forces the whole chrome dark in this light-app render.
    @Test
    fun `theme switch forces dark chrome in light app`() {
        capture(name = "browser-forced-dark-in-light.png") {
            radio("Dark").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `theme switch forces light chrome in dark app`() {
        capture(darkTheme = true, name = "browser-forced-light-in-dark.png") {
            radio("Light").performClick()
            composeRule.waitForIdle()
        }
    }

    // Ticket 09 radios: one tap lands the style (no cycling); SecondaryButton is the
    // transparent-ish case the feedback was about (outlined, no fill): it vanishes into
    // the checkerboard but reads on the solid styles.
    @Test
    fun `backdrop style switch fills previews with surface`() {
        capture(name = "browser-backdrop-surface.png") {
            radio("Surface").performClick()
            composeRule.waitForIdle()
            expandButtons()
        }
    }

    @Test
    fun `backdrop style switch fills previews with black`() {
        capture(name = "browser-backdrop-black.png") {
            radio("Black").performClick()
            composeRule.waitForIdle()
            expandButtons()
        }
    }

    // Detail panes ride the same session choice: pick the style on the home radio row,
    // then open a component and capture the re-filled light+dark pair.
    @Test
    fun `detail panes follow the backdrop style switch`() {
        capture(name = "browser-backdrop-detail-white.png") {
            radio("White").performClick()
            composeRule.waitForIdle()
            expandButtons()
            composeRule.onNodeWithText("PrimaryButton").performClick()
            composeRule.waitForIdle()
        }
    }

    // Ticket 09 DoD: the detail screen's own radio row themes the VARIANT pane — RTL
    // selected, then Dark → the RTL pane renders dark (VariantEnvironment's uiMode).
    @Test
    fun `detail rtl variant renders dark under the dark override`() {
        capture(name = "browser-detail-rtl-dark.png") {
            expandButtons()
            composeRule.onNodeWithText("PrimaryButton").performClick()
            composeRule.waitForIdle()
            // Variant selection stays chips (round-3 ruling); the radio row only themes.
            composeRule.onNodeWithText("RTL").performClick()
            composeRule.waitForIdle()
            radio("Dark").performClick()
            composeRule.waitForIdle()
        }
    }

    // Ticket 06 bulk actions: one tap expands EVERY group; the trailing chip flips to
    // "Collapse all" exactly when the persisted set covers all keys, so the flip in
    // the golden doubles as the allExpanded assertion.
    @Test
    fun `expand all opens every group`() {
        capture(name = "browser-all-expanded.png") {
            composeRule.onNodeWithText("Expand all").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `collapse all closes every opened group`() {
        capture(name = "browser-all-collapsed.png") {
            composeRule.onNodeWithText("Expand all").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Collapse all").performClick()
            composeRule.waitForIdle()
        }
    }

    // The bulk action must ride the same write-through path as single toggles so the
    // state survives process death: assert the pref file after each click (apply() is
    // disk-backed — the same persistence ticket 01 proved across a restart), not just
    // pixels.
    @Test
    fun `expand all writes through to prefs and collapse all clears them`() {
        runBrowser {
            composeRule.onNodeWithText("Expand all").performClick()
            composeRule.waitForIdle()
            assertTrue(storedExpandedGroups().isNotEmpty())
            composeRule.onNodeWithText("Collapse all").performClick()
            composeRule.waitForIdle()
            assertTrue(storedExpandedGroups().isEmpty())
        }
    }

    // Ticket 08: the detail round trip must return to the SAME list state (hoisted
    // LazyListState), not reset to the top. Deep-offset restoration itself is proven
    // on-device (evidence/08-scroll-before-detail.png vs 08-scroll-after-back.png —
    // byte-identical below the status bar): Robolectric cannot reproduce it because
    // this stack never applies lazy scroll requests — touch drags, semantics
    // ScrollBy/ScrollToIndex and even LazyListState(initialFirstVisibleItemIndex)
    // all render at the top, so the harness can only exercise the round trip at the
    // initial offset. Still guards: back returns to the LIST (not a dead state) with
    // expansion and the row intact.
    @Test
    fun `detail round trip returns to the browser list intact`() {
        runBrowser {
            expandButtons()
            composeRule.onNodeWithText("PrimaryButton").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Back to browser").assertExists()
            composeRule.onNodeWithContentDescription("Back to browser").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("PrimaryButton").assertExists()
            composeRule.onNodeWithText("BUTTONS").assertExists()
        }
    }

    private fun storedExpandedGroups(): Set<String> =
        (RuntimeEnvironment.getApplication() as Context)
            .getSharedPreferences(ShowkaseBrowserPrefs.PREFERENCES_FILE, Context.MODE_PRIVATE)
            .getStringSet(ShowkaseBrowserPrefs.KEY_EXPANDED_GROUPS, emptySet())
            .orEmpty()

    private fun expandButtons() {
        // CollapsibleGroup renders the group name uppercased; the whole header row is
        // clickable and merges the text node, so clicking the text hits the toggle.
        composeRule.onNodeWithText("BUTTONS").performClick()
        composeRule.waitForIdle()
    }

    // Ticket 09 radio option lookup: the option merges its label into one
    // Role.RadioButton node. A bare text match would also hit same-named catalog
    // content (e.g. a Surface color token), so the role disambiguates.
    private fun radio(label: String) = composeRule.onNode(
        hasText(label) and SemanticsMatcher("has radio role") { node ->
            node.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
        },
    )

    // Composition runner shared by capture() and the plain-assertion tests: setContent
    // on the real entry point, then interact.
    private fun runBrowser(darkTheme: Boolean = false, fontScale: Float = 1f, interact: () -> Unit = {}) {
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
    }

    private fun capture(darkTheme: Boolean = false, name: String, fontScale: Float = 1f, interact: () -> Unit = {}) {
        runBrowser(darkTheme, fontScale, interact)
        composeRule.onRoot()
            .captureRoboImage(File(SNAPSHOT_DIR, name).absolutePath)
    }

    companion object {
        // Committed inside the module (NOT gitignored .scratch/) so a fresh clone can
        // verify against goldens instead of having to re-record them.
        private val SNAPSHOT_DIR = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots")
    }
}
