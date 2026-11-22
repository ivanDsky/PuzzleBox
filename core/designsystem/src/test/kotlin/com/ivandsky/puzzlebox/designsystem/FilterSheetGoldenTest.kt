package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders of the filter component (epic picker-filters, ticket 03) on a phone, light and dark: the chips, the single and multi sheets open and
 * the multi page. Review captures, never a gate (ADR-0033): `-Proborazzi.record=true` writes untracked PNGs to `snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class FilterSheetGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `chips`() = composeRule.render("FilterChips", dark = false) { ChipsScene() }

    @Test
    fun `chips in the dark theme`() = composeRule.render("FilterChips_dark", dark = true) { ChipsScene() }

    @Test
    fun `the longest chip at font scale 1 point 3`() = composeRule.render("FilterChips_longest_font13", dark = false, fontScale = 1.3f) { LongestChipScene() }

    @Test
    fun `the longest chip at font scale 1 point 3 in the dark theme`() = composeRule.render("FilterChips_longest_font13_dark", dark = true, fontScale = 1.3f) { LongestChipScene() }

    @Test
    fun `single sheet`() = composeRule.render("SingleFilterSheet", dark = false, target = SHEET) { SingleSheetScene() }

    @Test
    fun `single sheet in the dark theme`() = composeRule.render("SingleFilterSheet_dark", dark = true, target = SHEET) { SingleSheetScene() }

    @Test
    fun `single sheet with supporting lines`() = composeRule.render("SingleFilterSheet_supporting", dark = false, target = SHEET) { StatsSheetScene(allLabel = "Any window") }

    @Test
    fun `single sheet without an all row`() = composeRule.render("SingleFilterSheet_no_all", dark = false, target = SHEET) { StatsSheetScene() }

    @Test
    fun `single sheet with a footer in the dark theme`() = composeRule.render("SingleFilterSheet_footer_dark", dark = true, target = SHEET) { StatsSheetScene(footer = "Friends comparison keeps its own windows.") }

    @Test
    fun `multi sheet`() = composeRule.render("MultiFilterSheet", dark = false, target = SHEET) { MultiSheetScene() }

    @Test
    fun `multi sheet in the dark theme`() = composeRule.render("MultiFilterSheet_dark", dark = true, target = SHEET) { MultiSheetScene() }

    @Test
    fun `multi sheet at font scale 1 point 3`() = composeRule.render("MultiFilterSheet_font13", dark = false, fontScale = 1.3f, target = SHEET) { MultiSheetScene() }

    @Test
    fun `multi page`() = composeRule.render("MultiFilterPage", dark = false) { MultiPageScene() }

    @Test
    fun `multi page in the dark theme`() = composeRule.render("MultiFilterPage_dark", dark = true) { MultiPageScene() }
}

internal const val SHEET = "x.filter.sheet"

private val Games = listOf("Queens" to GameGlyphs.Queens, "Zip" to GameGlyphs.Zip, "Tango" to GameGlyphs.Tango, "Mini Sudoku" to GameGlyphs.Sudoku, "Strands" to GameGlyphs.Strands)
private val Sources = listOf("From friends", "Community", "Past days", "A source with a name long enough to wrap onto a second line")

private fun gameOptions() = Games.map { (name, glyph) -> FilterOption(name, name, icon = { tint -> Icon(glyph, contentDescription = null, tint = tint) }) }

private fun sourceOptions() = Sources.map { FilterOption(it, it) }

@Composable
internal fun ChipsScene() {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChipRow {
            SingleFilterChip("Freshness", "Any", listOf(FilterOption("new", "New")), selected = null, onSelect = {})
            SingleFilterChip("Freshness", "Any", listOf(FilterOption("new", "New")), selected = "new", onSelect = {})
        }
        FilterChipRow {
            MultiFilterChip("Source", "All", sourceOptions(), selected = setOf("Community", "Past days"), onToggle = {}, onClearGroup = {})
            DropdownFilterChip("Source", picked = false, open = true, onClick = {})
        }
        FilterChipRow {
            DropdownFilterChip("Source · 2", picked = true, open = true, onClick = {})
            DropdownFilterChip("Source", picked = false, open = false, onClick = {}, enabled = false)
        }
    }
}

@Composable
internal fun LongestChipScene() {
    FilterChipRow(Modifier.padding(Spacing.lg)) {
        MultiFilterChip("Source", "All", sourceOptions(), selected = setOf(Sources.last()), onToggle = {}, onClearGroup = {})
    }
}

@Composable
internal fun SingleSheetScene() = SingleFilterSheet("Game", "All games", gameOptions(), selected = "Zip", onSelect = {}, onDismiss = {}, tag = "x.filter", modifier = Modifier.testTag(SHEET))

@Composable
internal fun StatsSheetScene(allLabel: String? = null, footer: String? = null) = SingleFilterSheet(
    "Window",
    allLabel,
    listOf(FilterOption("w", "Last 7 days", supporting = "Mon to Sun"), FilterOption("m", "Last 30 days", supporting = "Rolling month"), FilterOption("a", "All time")),
    selected = "w",
    onSelect = {},
    onDismiss = {},
    tag = "x.filter",
    modifier = Modifier.testTag(SHEET),
    footer = footer,
)

@Composable
internal fun MultiSheetScene() = MultiFilterSheet("Source", "All sources", sourceOptions(), selected = setOf("From friends", Sources.last()), onToggle = {}, onClearGroup = {}, onDismiss = {}, modifier = Modifier.testTag(SHEET))

@Composable
internal fun MultiPageScene() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        MultiFilterPage("Source", "All sources", sourceOptions(), selected = setOf("From friends", Sources.last()), onToggle = {}, onClearGroup = {}, onBack = {})
    }
}

/** Shows [content] in the light or dark theme, lets it settle and writes `snapshots/<name>.png`; [target] is the tag of a sheet's node, null is the whole root. */
internal fun ComposeContentTestRule.render(name: String, dark: Boolean, fontScale: Float = 1f, target: String? = null, content: @Composable () -> Unit) {
    mainClock.autoAdvance = false
    setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            PuzzleBoxTheme(darkTheme = dark) { content() }
        }
    }
    mainClock.advanceTimeBy(1000)
    val path = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/$name.png").canonicalFile.absolutePath
    (if (target == null) onRoot() else onNodeWithTag(target)).captureRoboImage(path)
}
