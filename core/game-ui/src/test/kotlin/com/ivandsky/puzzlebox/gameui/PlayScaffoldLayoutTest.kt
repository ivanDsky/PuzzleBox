package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A15: on a landscape tablet the controls sit in a toolbar above a board that fills its
 * pane, and the side pane narrows to ~30% and hosts the route's slot; a phone keeps the
 * action bar under the caption.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1848dp-h1000dp")
class PlayScaffoldLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object Undo : PlayUndo {
        override val canUndo = true
        override val canRedo = false
        override fun undo() = Unit
        override fun redo() = Unit
    }

    private object Hints : PlayHints {
        override val hintsUsed = 0
        override val hintEnabled = true
        override val hintCaption: String? = null
        override fun requestHint() = Unit
    }

    private fun setScreen(
        layout: PlayLayout,
        width: Int,
        height: Int,
        slot: @Composable ColumnScope.() -> Unit = { Text("stats", Modifier.testTag("slot")) },
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPlaySidePanel provides slot) {
                    AdaptivePlayScaffold(
                        layout = layout,
                        modifier = Modifier.requiredSize(width.dp, height.dp),
                        // Honours the cap the way Tango's and Zip's boards do.
                        board = {
                            val cap = LocalPlayBoardPaneCap.current ?: PlayBoardMaxWidth
                            Box(
                                Modifier.playBoardPane().padding(horizontal = Spacing.lg),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(Modifier.widthIn(max = cap).heightIn(max = cap).aspectRatio(1f).testTag("board"))
                            }
                        },
                        side = { PlayChrome(clock = null, hints = Hints, banner = null) },
                        actions = { PlayActionBar(Undo, Hints, clear = null, accent = AccentSlot.Teal) },
                    )
                }
            }
        }
    }

    @Test
    fun `a landscape tablet puts the toolbar above a board that fills its pane`() {
        setScreen(PlayLayout.TwoPane, 1848, 1000)
        val board = composeRule.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val toolbar = composeRule.onNodeWithTag("play.action_bar").getUnclippedBoundsInRoot()
        val side = composeRule.onNodeWithTag("play.side_pane").getUnclippedBoundsInRoot()

        assertTrue("board width ${board.width}", board.width > 700.dp)
        assertTrue("toolbar $toolbar above board $board", toolbar.bottom <= board.top)
        assertTrue("toolbar $toolbar spans the board's columns $board", toolbar.left >= board.left && toolbar.right <= board.right)
        assertEquals("side pane ≈30%", 0.3f, side.width.value / 1848f, 0.01f)
        composeRule.onNode(hasTestTag("slot") and hasAnyAncestor(hasTestTag("play.side_pane"))).assertExists()
    }

    @Test
    fun `a slot taller than the side pane scrolls under the game's chrome, never over it`() {
        setScreen(PlayLayout.TwoPane, 1848, 1000) { Box(Modifier.height(5000.dp).testTag("slot")) }
        val pane = composeRule.onNodeWithTag("play.side_pane").getUnclippedBoundsInRoot()
        val chrome = composeRule.onNodeWithTag("play.side_panel").getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag("play.side_slot").getUnclippedBoundsInRoot()

        assertTrue("chrome $chrome keeps its height", chrome.height > 0.dp)
        assertTrue("slot $slot under chrome $chrome", slot.top >= chrome.bottom)
        assertTrue("slot $slot bounded by pane $pane", slot.bottom <= pane.bottom)
    }

    @Test
    fun `a phone keeps the action bar under the caption and no slot`() {
        setScreen(PlayLayout.SinglePane, 360, 800)
        val board = composeRule.onNodeWithTag("board").getUnclippedBoundsInRoot()
        val chrome = composeRule.onNodeWithTag("play.side_panel").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag("play.action_bar").getUnclippedBoundsInRoot()

        assertTrue("chrome $chrome under board $board", chrome.top >= board.bottom)
        assertTrue("bar $bar under chrome $chrome", bar.top >= chrome.bottom)
        assertEquals("bar sits on the screen's bottom margin", 800.dp - Spacing.sm, bar.bottom)
        composeRule.onNodeWithTag("slot").assertDoesNotExist()
    }
}
