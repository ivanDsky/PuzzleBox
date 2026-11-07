package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the Studio's chrome lands by window: the test-cleared toast never covers Test solve and
 * stays in the tablet's side pane; a phone's verdict card is a sheet, its pill a glyph under
 * 360 dp; the phone's board slot spans the content width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp")
class StudioChromePlacementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var cardOpen by mutableStateOf(false)

    private fun studio(layout: PlayLayout, testCleared: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme {
                StudioScreen(
                    title = "Untitled 3",
                    subtitle = "Queens 6×6",
                    saving = false,
                    verdict = NONE,
                    layout = layout,
                    onBack = {},
                    onRename = {},
                    onDuplicate = {},
                    onDelete = {},
                    board = { Box(it) },
                    toolStrip = { Box(it.height(STRIP_HEIGHT).testTag(STRIP)) },
                    cardOpen = cardOpen,
                    onShowCard = { cardOpen = true },
                    onDismissCard = { cardOpen = false },
                    actionArea = { ActionArea(passed = null, canTestSolve = true, onTestSolve = {}, blocks = emptyList(), modifier = it) },
                    testCleared = testCleared,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on a phone the toast sits at the bottom of the board slot, above the tool strip`() {
        studio(PlayLayout.SinglePane, testCleared = true)
        composeRule.waitUntil { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }

        val toast = composeRule.onNodeWithTag(TOAST).getBoundsInRoot()
        val slot = composeRule.onNodeWithTag(BOARD_SLOT).getBoundsInRoot()
        val strip = composeRule.onNodeWithTag(STRIP).getBoundsInRoot()
        val cta = composeRule.onNodeWithTag(TEST_SOLVE).getBoundsInRoot()
        assertTrue("toast bottom ${toast.bottom} over the strip's top ${strip.top}", toast.bottom <= strip.top)
        assertTrue("toast top ${toast.top} above the slot's top ${slot.top}", toast.top >= slot.top)
        assertTrue("toast bottom ${toast.bottom} over Test solve's top ${cta.top}", toast.bottom <= cta.top)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `on a tablet the toast stays inside the side pane, above Test solve`() {
        studio(PlayLayout.TwoPane, testCleared = true)
        composeRule.waitUntil { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }

        val toast = composeRule.onNodeWithTag(TOAST).getBoundsInRoot()
        val pane = composeRule.onNodeWithTag(SIDE_PANE).getBoundsInRoot()
        val cta = composeRule.onNodeWithTag(TEST_SOLVE).getBoundsInRoot()
        assertTrue("toast left ${toast.left} outside the pane at ${pane.left}", toast.left >= pane.left)
        assertTrue("toast bottom ${toast.bottom} over Test solve's top ${cta.top}", toast.bottom <= cta.top)
    }

    @Test
    fun `under 360 dp the pill shows its glyph alone`() {
        studio(PlayLayout.SinglePane)

        composeRule.onNodeWithText("✕", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("No solution", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `from 360 dp the pill shows its label`() {
        studio(PlayLayout.SinglePane)

        composeRule.onNodeWithText("No solution", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `on a phone the card opens as a sheet that closes on dismiss`() {
        studio(PlayLayout.SinglePane)

        composeRule.onNodeWithTag(PILL).performClick()
        composeRule.onNodeWithTag(SHEET).assertExists()
        composeRule.onNodeWithTag(CARD).assertExists()

        composeRule.runOnIdle { cardOpen = false }
        composeRule.onNodeWithTag(SHEET).assertDoesNotExist()
    }

    @Test
    fun `on a phone the board slot spans the content width`() {
        studio(PlayLayout.SinglePane)

        val slot = composeRule.onNodeWithTag(BOARD_SLOT).getBoundsInRoot()
        val width = slot.right - slot.left
        assertTrue("slot width $width", width >= 320.dp - 32.dp)
    }

    private companion object {
        const val TOAST = "create.toast.testCleared"
        const val TEST_SOLVE = "create.action.testSolve"
        const val SIDE_PANE = "create.studio.sidePane"
        const val PILL = "create.editor.pill"
        const val SHEET = "create.verdict.sheet"
        const val CARD = "create.verdict.card"
        const val BOARD_SLOT = "create.editor.board"
        const val STRIP = "test.toolStrip"

        val STRIP_HEIGHT = 96.dp

        val NONE = Verdict.None(emptySet())
    }
}
