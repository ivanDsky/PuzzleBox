package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The snackbar a Finish notice raises: the notice's own words, once per notice, and nothing before one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinishNoticeToastTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = FinishNoticeState()

    private fun show() = composeRule.setContent {
        PuzzleBoxTheme { FinishNoticeToast(state, Modifier) }
    }

    @Test
    fun `nothing shows before a notice`() {
        show()

        composeRule.onAllNodesWithTag(TOAST).assertCountEquals(0)
    }

    @Test
    fun `a give-up says its cause's words and is spent once shown`() {
        show()

        state.raise(FinishNotice.NoFix)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TOAST).assertExists()
        composeRule.onNodeWithText("Couldn't fix this board in time. Change a few highlighted cells and try again.").assertExists()
        assertNull("shown once, so a rotation never repeats it", state.pending.value)
    }

    @Test
    fun `a missed level says both levels under the same tag`() {
        show()

        state.raise(FinishNotice.Missed(got = Difficulty.Level.Medium, wanted = Difficulty.Level.Hard))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TOAST).assertExists()
        composeRule.onNodeWithText("Finished at Medium \u2014 Hard wasn't found in time. Press Finish to try again.").assertExists()
    }

    private companion object {
        const val TOAST = "create.toast.finishGaveUp"
    }
}
