package com.ivandsky.puzzlebox.play

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PhoneBoardCap
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [GameLoading] holds the place of the board that replaces it: on a wide window the loading square sits in the
 * board pane (the left 70%) centred, as the loaded board does, not at the window's top-left; a phone keeps
 * its single column.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameLoadingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(layout: PlayLayout) {
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { GameLoading(layout, onBack = {}) } }
        composeRule.waitForIdle()
    }

    private fun loading(): Rect = composeRule.onNodeWithTag("game.loading").getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    @Test
    @Config(qualifiers = GOLDEN_TABLET)
    fun `on a landscape tablet the skeleton is a centred square in the board pane`() {
        show(PlayLayout.TwoPane)

        val box = loading()
        val window = composeRule.onRoot().getBoundsInRoot()
        val paneCentre = window.right.value * BOARD_PANE_SHARE / 2
        assertEquals("centred in the board pane, not at the window's left", paneCentre, box.center.x, ALIGN_TOLERANCE)
        assertEquals("a square", box.width, box.height, ALIGN_TOLERANCE)
        assertTrue("no wider than a board", box.width <= PhoneBoardCap.value)
        assertTrue("below the bar, in the middle of the page", box.center.y in window.bottom.value * 0.4f..window.bottom.value * 0.65f)
    }

    @Test
    @Config(qualifiers = GOLDEN_PHONE)
    fun `on a phone the skeleton keeps the full-width column`() {
        show(PlayLayout.SinglePane)

        val box = loading()
        val window = composeRule.onRoot().getBoundsInRoot()
        assertEquals(0f, box.left, ALIGN_TOLERANCE)
        assertEquals(window.right.value, box.width, ALIGN_TOLERANCE)
    }

    private companion object {
        const val BOARD_PANE_SHARE = 0.7f
        const val ALIGN_TOLERANCE = 2f
    }
}
