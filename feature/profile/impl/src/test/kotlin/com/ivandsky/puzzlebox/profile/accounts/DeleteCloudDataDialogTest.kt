package com.ivandsky.puzzlebox.profile.accounts

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The destructive confirm for Delete my cloud data on its own (criterion 17): its copy, Cancel and Delete. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DeleteCloudDataDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var confirms = 0
    private var cancels = 0

    private fun setDialog() {
        composeRule.setContent { PuzzleBoxTheme { DeleteCloudDataDialog(onConfirm = { confirms++ }, onCancel = { cancels++ }) } }
    }

    @Test
    fun `the dialog says what goes, what stays and that signing in again uploads the history again`() {
        setDialog()

        composeRule.onNodeWithText("Delete my cloud data?").assertExists()
        composeRule.onNodeWithText("This removes your synced history and saved boards from your account", substring = true).assertExists()
        composeRule.onNodeWithText("Everything stays on this phone.", substring = true).assertExists()
        composeRule.onNodeWithText("Signing in again uploads this phone's history again.", substring = true).assertExists()
        composeRule.onNodeWithText("It can't be undone.", substring = true).assertExists()
    }

    @Test
    fun `cancel does nothing but cancel`() {
        setDialog()

        composeRule.onNodeWithTag("profile.google.delete.cancel").performClick()

        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test
    fun `delete calls once and never cancels`() {
        setDialog()

        composeRule.onNodeWithTag("profile.google.delete.confirm").performClick()

        assertEquals(1, confirms)
        assertEquals(0, cancels)
    }
}
