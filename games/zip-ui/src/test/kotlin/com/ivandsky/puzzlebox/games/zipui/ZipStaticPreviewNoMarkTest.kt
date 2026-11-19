package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A static Zip board (the Community preview, and every other board off the live play screen) shows no
 * next-number ring: the old outer ring is gone, and the pulse runs only on live play (backlog #1540).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ZipStaticPreviewNoMarkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the static readable board draws no next-number ring`() {
        composeRule.setContent { ZipReadableBoardSample(dark = false) }

        // Cell 21 holds the next number ("3"): the board is drawn with that number still to reach.
        composeRule.onNodeWithTag("play.cell_21").assertExists()
        composeRule.onAllNodesWithTag("play.next_number_ring").assertCountEquals(0)
        composeRule.onAllNodesWithTag("play.next_number_halo").assertCountEquals(0)
    }
}
