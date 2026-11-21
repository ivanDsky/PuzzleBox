package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// segmented-replace 02: the settings-style radio rows, as a unit.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ChoiceRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val modes = listOf("System", "Light", "Dark")
    private val hints = mapOf("System" to "Follows your phone's setting", "Light" to "Always light", "Dark" to "Always dark")

    private fun rows(
        picked: MutableList<String> = mutableListOf(),
        selected: String = "Light",
        enabled: Boolean = true,
        optionEnabled: (String) -> Boolean = { true },
        disabledReason: (String) -> String? = { null },
        labels: (String) -> String = { it },
        descriptions: (String) -> String = { hints.getValue(it) },
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PuzzleBoxTheme {
                    Box(Modifier.width(360.dp)) {
                        ChoiceRows(
                            options = modes,
                            selected = selected,
                            onSelect = { picked += it },
                            label = labels,
                            description = descriptions,
                            tag = { "row.$it" },
                            enabled = enabled,
                            optionEnabled = optionEnabled,
                            disabledReason = disabledReason,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `every row is at least 56dp tall`() {
        rows()

        modes.forEach { composeRule.onNodeWithTag("row.$it").assertHeightIsAtLeast(56.dp) }
    }

    @Test
    fun `a tap on an enabled row reports its option`() {
        val picked = mutableListOf<String>()
        rows(picked)

        composeRule.onNodeWithTag("row.Dark").performClick()

        assertEquals(listOf("Dark"), picked)
    }

    @Test
    fun `a tap on a disabled option reports nothing and its neighbour still does`() {
        val picked = mutableListOf<String>()
        rows(picked, optionEnabled = { it != "Dark" })

        composeRule.onNodeWithTag("row.Dark").performClick()
        composeRule.onNodeWithTag("row.System").performClick()

        assertEquals(listOf("System"), picked)
    }

    @Test
    fun `a tap in a disabled group reports nothing`() {
        val picked = mutableListOf<String>()
        rows(picked, enabled = false)

        modes.forEach { composeRule.onNodeWithTag("row.$it").performClick() }

        assertTrue(picked.isEmpty())
    }

    @Test
    fun `exactly the chosen row is selected and every row is a radio button in one group`() {
        rows(selected = "Light")

        composeRule.onNodeWithTag("row.System").assertIsNotSelected()
        composeRule.onNodeWithTag("row.Light").assertIsSelected()
        composeRule.onNodeWithTag("row.Dark").assertIsNotSelected()
        modes.forEach {
            composeRule.onNodeWithTag("row.$it").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        assertTrue(composeRule.onNodeWithTag("row.Light").fetchSemanticsNode().parent!!.config.contains(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun `a disabled option shows its reason in place of its description and announces it`() {
        rows(optionEnabled = { it != "Dark" }, disabledReason = { "Needs a newer phone" })

        composeRule.onNodeWithText("Needs a newer phone").assertExists()
        composeRule.onNodeWithText("Always dark").assertDoesNotExist()
        composeRule.onNodeWithText("Always light").assertExists()
        composeRule.onNodeWithTag("row.Dark")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs a newer phone"))
        composeRule.onNodeWithTag("row.Light").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `long labels at font scale 1 point 3 wrap without an ellipsis or a clipped line`() {
        val wrapping = "Match my phone with a very long title that has to wrap onto lines"
        val long = mapOf("System" to wrapping, "Light" to "Puzzles from earlier days", "Dark" to "New puzzles from today")
        rows(labels = { long.getValue(it) }, fontScale = 1.3f)

        long.values.forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertFalse("$text is ellipsized", (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
            assertTrue("$text is clipped", layout.size.height >= layout.multiParagraph.height)
        }
        val wrapped = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(wrapping, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(wrapped)
        assertTrue("the long title wraps", wrapped.single().lineCount > 1)
    }
}
