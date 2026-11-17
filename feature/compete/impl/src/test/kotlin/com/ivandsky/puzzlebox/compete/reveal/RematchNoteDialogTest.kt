package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The self Rematch refusal is the app's dialog: its words show, OK is a 48 dp target and dismisses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RematchNoteDialogTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the refusal shows its words and OK closes it from a 48 dp target`() {
        var oks = 0
        rule.setContent { PuzzleBoxTheme { RematchNoteDialog("No solved puzzles left to draw.", onOk = { oks++ }) } }
        rule.waitForIdle()

        rule.onNodeWithText("No solved puzzles left to draw.").assertIsDisplayed()
        rule.assertTouchTargetsAtLeast48()
        rule.onNodeWithTag("reveal.rematch.ok").performClick()

        assertEquals(1, oks)
    }
}
