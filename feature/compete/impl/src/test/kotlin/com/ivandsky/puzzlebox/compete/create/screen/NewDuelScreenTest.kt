package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.compete.create.actionsWith
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The New duel page: its form under a bar whose arrow is Back on the phone and Close in the tablet's pane. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NewDuelScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var backs = 0
    private var creates = 0

    private fun show(pane: Boolean = false) = rule.setContent {
        PuzzleBoxTheme { NewDuelScreen(friend(), actionsWith(onCreate = { creates++ }), onBack = { backs++ }, pane = pane) }
    }

    @Test
    fun `the page shows its form and Back leaves`() {
        show()

        rule.onNodeWithTag("duel.new.screen").assertIsDisplayed()
        rule.onNodeWithTag("duel.create").performClick()
        assertEquals(1, creates)
        rule.onNodeWithTag("duel.new.back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `in the pane the arrow is Close, not Back`() {
        show(pane = true)

        rule.onAllNodesWithTag("duel.new.back").assertCountEquals(0)
        rule.onNodeWithTag("duel.new.close").performClick()
        assertEquals(1, backs)
    }
}
