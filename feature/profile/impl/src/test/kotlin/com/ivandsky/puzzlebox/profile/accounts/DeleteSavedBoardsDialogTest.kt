package com.ivandsky.puzzlebox.profile.accounts

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.LinkedInBoards
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The destructive confirm for Delete saved boards, alone and behind the LinkedIn overflow (A27). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DeleteSavedBoardsDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var confirms = 0
    private var cancels = 0
    private var disconnects = 0
    private val summary = LinkedInAccountView("Mira Voss", null, 142, 5, 213, listOf("Zip"), NOW - MINUTE)

    private fun setDialog() {
        composeRule.setContent { PuzzleBoxTheme { DeleteSavedBoardsDialog(onConfirm = { confirms++ }, onCancel = { cancels++ }) } }
    }

    private fun setRow(boards: LinkedInBoards?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LinkedInRow(
                    state = LinkedInRowState.Connected(summary),
                    nowMs = NOW,
                    onConnect = {},
                    onResume = {},
                    onDisconnect = { disconnects++ },
                    boards = boards,
                    onDeleteSavedBoards = { confirms++ },
                )
            }
        }
    }

    private fun openDeleteBoards() {
        composeRule.onNodeWithTag("profile.linkedin.overflow").performClick()
        composeRule.onNodeWithText("Delete saved boards").performClick()
    }

    @Test
    fun `the dialog names the deletion and says what stays on other phones`() {
        setDialog()

        composeRule.onNodeWithText("Delete saved boards?").assertExists()
        composeRule.onNodeWithText("Another phone keeps its own copy", substring = true).assertExists()
    }

    @Test
    fun `confirm calls deletion once and cancel never`() {
        setDialog()

        composeRule.onNodeWithTag("profile.linkedin.delete-boards.confirm").performClick()

        assertEquals(1, confirms)
        assertEquals(0, cancels)
    }

    @Test
    fun `cancel does not call deletion`() {
        setDialog()

        composeRule.onNodeWithTag("profile.linkedin.delete-boards.cancel").performClick()

        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test
    fun `the overflow asks first and deletes only on Delete`() {
        setRow(LinkedInBoards(savedToAccount = true, canDelete = true))
        composeRule.onNodeWithTag("profile.linkedin.delete-boards.dialog").assertDoesNotExist()

        openDeleteBoards()
        composeRule.onNodeWithTag("profile.linkedin.delete-boards.dialog").assertExists()
        assertEquals(0, confirms)

        composeRule.onNodeWithTag("profile.linkedin.delete-boards.confirm").performClick()

        assertEquals(1, confirms)
        assertEquals(0, disconnects)
        composeRule.onNodeWithTag("profile.linkedin.delete-boards.dialog").assertDoesNotExist()
    }

    @Test
    fun `cancelling the confirm deletes nothing`() {
        setRow(LinkedInBoards(savedToAccount = true, canDelete = true))

        openDeleteBoards()
        composeRule.onNodeWithTag("profile.linkedin.delete-boards.cancel").performClick()

        assertEquals(0, confirms)
        composeRule.onNodeWithTag("profile.linkedin.delete-boards.dialog").assertDoesNotExist()
    }

    @Test
    fun `delete saved boards is not offered while one is running`() {
        setRow(LinkedInBoards(savedToAccount = true, canDelete = true, deleting = true))

        composeRule.onNodeWithTag("profile.linkedin.overflow").performClick()

        composeRule.onNodeWithTag("profile.linkedin.delete-boards").assertIsNotEnabled()
    }

    @Test
    fun `without cloud boards the overflow offers Disconnect only`() {
        setRow(boards = null)

        composeRule.onNodeWithTag("profile.linkedin.overflow").performClick()

        composeRule.onNodeWithTag("profile.linkedin.disconnect").assertExists()
        composeRule.onNodeWithTag("profile.linkedin.delete-boards").assertDoesNotExist()
    }

    @Test
    fun `cloud sync alone without cloud boards offers no delete`() {
        setRow(LinkedInBoards(savedToAccount = true, canDelete = false))

        composeRule.onNodeWithTag("profile.linkedin.overflow").performClick()

        composeRule.onNodeWithTag("profile.linkedin.delete-boards").assertDoesNotExist()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }
}
