package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rules sheet (rules-sheet 03): title, numbered rules, controls, and the two actions that close it first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RulesSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brands = mapOf(
        "zip" to GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
        "tango" to GameBrand(AccentSlot.Orange, GameGlyphs.Tango),
        "queens" to GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
        "strands" to GameBrand(AccentSlot.Pink, GameGlyphs.Strands),
        "minisudoku" to GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku),
    )

    private var dismissed = 0
    private var replayed = 0

    private fun show(game: String, copy: RulesSheetCopy = checkNotNull(rulesSheetCopy(GameId(game)))) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RulesSheet(copy, brands.getValue(game), onDismiss = { dismissed++ }, onReplayTutorial = { replayed++ })
            }
        }
        composeRule.waitUntil { composeRule.onAllNodesWithTag("rules.sheet").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `zip's sheet lists its three rules numbered and its controls`() {
        show("zip")

        composeRule.onNodeWithText("How to play").assertExists()
        composeRule.onNodeWithText("RULES").assertExists()
        composeRule.onNodeWithText("CONTROLS").assertExists()
        val copy = checkNotNull(rulesSheetCopy(GameId("zip")))
        assertEquals(3, copy.rules.size)
        copy.rules.forEachIndexed { index, rule ->
            composeRule.onNodeWithText("${index + 1}").assertExists()
            composeRule.onNodeWithText(rule).assertExists()
        }
        copy.controls.forEach { composeRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `tango's sheet shows its rules and controls`() {
        show("tango")

        val copy = checkNotNull(rulesSheetCopy(GameId("tango")))
        (copy.rules + copy.controls).forEach { composeRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `a game with no controls lines hides the Controls heading`() {
        show("zip", RulesSheetCopy(rules = listOf("One", "Two", "Three"), controls = emptyList()))

        composeRule.onNodeWithText("RULES").assertExists()
        composeRule.onAllNodesWithText("CONTROLS").assertCountEquals(0)
    }

    @Test
    fun `Got it hides the sheet and then calls onDismiss, not onReplayTutorial`() {
        show("zip")

        composeRule.onNodeWithTag("rules.got_it").performScrollTo().performClick()
        composeRule.waitUntil { dismissed > 0 }

        assertEquals(1, dismissed)
        assertEquals(0, replayed)
    }

    @Test
    fun `Replay the tutorial hides the sheet and then calls onReplayTutorial, not onDismiss`() {
        show("zip")

        composeRule.onNodeWithTag("rules.replay").performScrollTo().performClick()
        composeRule.waitUntil { replayed > 0 }

        assertEquals(1, replayed)
        assertEquals(0, dismissed)
    }
}
