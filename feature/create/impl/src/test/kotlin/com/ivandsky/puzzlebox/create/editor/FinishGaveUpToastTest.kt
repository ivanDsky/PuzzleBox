package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The snackbar a failed Finish raises: this exact message, once per failure, and nothing before one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinishGaveUpToastTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = FinishGaveUpToastState()

    private fun show() = composeRule.setContent {
        PuzzleBoxTheme { FinishGaveUpToast(state, Modifier) }
    }

    @Test
    fun `nothing shows before a give-up`() {
        show()

        composeRule.onNodeWithText(MESSAGE).assertDoesNotExist()
    }

    @Test
    fun `a give-up says the message verbatim and is spent once shown`() {
        show()

        state.raise()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("create.toast.finishGaveUp").assertExists()
        composeRule.onNodeWithText(MESSAGE).assertExists()
        assertFalse("shown once, so a rotation never repeats it", state.pending.value)
    }

    private companion object {
        const val MESSAGE = "Too many unknowns to finish this draft. Place a few more and try again."
    }
}
