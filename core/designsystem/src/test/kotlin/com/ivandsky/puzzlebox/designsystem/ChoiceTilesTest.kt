package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// segmented-replace 01: the work-surface mode tiles and the message line under them, as a unit.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ChoiceTilesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val modes = listOf("Path", "Numbers", "Walls")

    private fun tiles(
        picked: MutableList<String> = mutableListOf(),
        selected: String = "Numbers",
        enabled: Boolean = true,
        optionEnabled: (String) -> Boolean = { true },
        labels: (String) -> String = { it },
        fontScale: Float = 1f,
        widthDp: Int = 360,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PuzzleBoxTheme {
                    Box(Modifier.width(widthDp.dp)) {
                        ChoiceTiles(
                            options = modes,
                            selected = selected,
                            onSelect = { picked += it },
                            label = labels,
                            icon = { PuzzleIcons.Check },
                            tag = { "tile.$it" },
                            accent = AccentSlot.Violet,
                            enabled = enabled,
                            optionEnabled = optionEnabled,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `every tile is at least 48dp tall`() {
        tiles()

        modes.forEach { composeRule.onNodeWithTag("tile.$it").assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun `a tap on an enabled tile reports its option`() {
        val picked = mutableListOf<String>()
        tiles(picked)

        composeRule.onNodeWithTag("tile.Walls").performClick()

        assertEquals(listOf("Walls"), picked)
    }

    @Test
    fun `a tap on a disabled option reports nothing and its neighbour still does`() {
        val picked = mutableListOf<String>()
        tiles(picked, optionEnabled = { it != "Walls" })

        composeRule.onNodeWithTag("tile.Walls").performClick()
        composeRule.onNodeWithTag("tile.Path").performClick()

        assertEquals(listOf("Path"), picked)
    }

    @Test
    fun `a tap in a disabled group reports nothing`() {
        val picked = mutableListOf<String>()
        tiles(picked, enabled = false)

        modes.forEach { composeRule.onNodeWithTag("tile.$it").performClick() }

        assertTrue(picked.isEmpty())
    }

    @Test
    fun `exactly the chosen tile is selected and every tile is a radio button`() {
        tiles(selected = "Numbers")

        composeRule.onNodeWithTag("tile.Path").assertIsNotSelected()
        composeRule.onNodeWithTag("tile.Numbers").assertIsSelected()
        composeRule.onNodeWithTag("tile.Walls").assertIsNotSelected()
        modes.forEach {
            composeRule.onNodeWithTag("tile.$it").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    @Test
    fun `a disabled option is disabled and says why through its state description`() {
        tiles(optionEnabled = { it != "Walls" })

        composeRule.onNodeWithTag("tile.Walls")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        composeRule.onNodeWithTag("tile.Path").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `the tiles are one selectable group`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                ChoiceTiles(
                    options = modes,
                    selected = "Path",
                    onSelect = {},
                    label = { it },
                    icon = { PuzzleIcons.Check },
                    tag = { "tile.$it" },
                    accent = AccentSlot.Violet,
                )
            }
        }

        composeRule.onNodeWithTag("tile.Path").assertExists()
        composeRule.onNodeWithTag("tile.Path").fetchSemanticsNode().parent!!.let { group ->
            assertTrue(group.config.contains(SemanticsProperties.SelectableGroup))
        }
    }

    @Test
    fun `long labels at font scale 1 point 3 wrap together without an ellipsis`() {
        val long = mapOf("Path" to "Draw the path", "Numbers" to "Numbers & walls", "Walls" to "Walls")
        tiles(labels = { long.getValue(it) }, fontScale = 1.3f, widthDp = 300)

        val heights = modes.map { composeRule.onNodeWithTag("tile.$it").getUnclippedBoundsInRoot().height }
        assertEquals("tiles share one height", 1, heights.distinct().size)
        long.values.forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertFalse("$text is cut", layouts.single().hasVisualOverflow)
        }
    }

    @Test
    fun `a blocked message has no retry and is not a live region`() {
        composeRule.setContent {
            PuzzleBoxTheme { ChoiceMessage("Cover every square with the path first.", isError = false) }
        }

        composeRule.onNodeWithText("Cover every square with the path first.").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `an error message is a live region and its retry reports a tap`() {
        var retried = 0
        composeRule.setContent {
            PuzzleBoxTheme { ChoiceMessage("Couldn't paint that region.", isError = true, onRetry = { retried++ }) }
        }

        composeRule.onNodeWithText("Couldn't paint that region.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithText("Try again").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, retried)
    }
}
