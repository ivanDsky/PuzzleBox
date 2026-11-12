package com.ivandsky.puzzlebox.profile.accounts

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.LinkedInBoards
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import com.ivandsky.puzzlebox.profile.linkedin.LinkedInRiskWarning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The LinkedIn row's and risk warning's copy with and without cloud sync (A27, A35). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LinkedInRowCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val summary = LinkedInAccountView("Mira Voss", null, 142, 5, 213, listOf("Zip"), NOW - MINUTE)
    private val synced = LinkedInBoards(savedToAccount = true, canDelete = true)

    private fun setRow(state: LinkedInRowState, boards: LinkedInBoards?, googleSignedIn: Boolean = true) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LinkedInRow(
                    state = state,
                    nowMs = NOW,
                    onConnect = {},
                    onResume = {},
                    onDisconnect = {},
                    boards = boards,
                    googleSignedIn = googleSignedIn,
                )
            }
        }
    }

    private fun setWarning(savedToAccount: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme { LinkedInRiskWarning(onAccept = {}, onCancel = {}, savedToAccount = savedToAccount) }
        }
    }

    @Test
    fun `the connect line without cloud sync keeps today's copy`() {
        setRow(LinkedInRowState.NotConnected, boards = null)

        composeRule.onNodeWithText("Import your own LinkedIn game results and see your connections' times on this phone.").assertExists()
    }

    @Test
    fun `the connect line with cloud sync says the boards are saved to the account`() {
        setRow(LinkedInRowState.NotConnected, synced)

        composeRule.onNodeWithText(
            "Import your own LinkedIn game results and see your connections' times. " +
                "The boards you read are saved to your own PuzzleBox account, readable only by you, and kept after Disconnect.",
        ).assertExists()
        composeRule.onNodeWithText("on this phone", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the risk warning without cloud sync says the times are never uploaded`() {
        setWarning(savedToAccount = false)

        composeRule.onNodeWithText("Your connections' times stay on this phone and are never uploaded.").performScrollTo().assertExists()
        composeRule.onNodeWithText("saved to your own PuzzleBox account", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the risk warning with cloud sync says the boards are saved to the account and kept after Disconnect`() {
        setWarning(savedToAccount = true)

        composeRule.onNodeWithText(
            "Your connections' boards are saved to your own PuzzleBox account while you're signed in with Google. " +
                "Only you can read them, and they are kept after Disconnect.",
        ).performScrollTo().assertExists()
        composeRule.onNodeWithText("never uploaded", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("§8.2", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `a connected row with Google signed out asks to sign in`() {
        setRow(LinkedInRowState.Connected(summary), synced, googleSignedIn = false)

        composeRule.onNodeWithText("Sign in with Google to keep these boards on every device").assertExists()
    }

    @Test
    fun `a paused row with Google signed out asks to sign in too`() {
        setRow(LinkedInRowState.Paused(summary, "reason", needsReconnect = true), synced, googleSignedIn = false)

        composeRule.onNodeWithText("Sign in with Google to keep these boards on every device").assertExists()
        composeRule.onNodeWithText("Reconnect").assertExists()
    }

    @Test
    fun `signed in to Google there is no sign-in hint`() {
        setRow(LinkedInRowState.Connected(summary), synced, googleSignedIn = true)

        composeRule.onNodeWithTag("profile.linkedin.sign-in-hint").assertDoesNotExist()
    }

    @Test
    fun `without cloud sync there is no sign-in hint even when Google is signed out`() {
        setRow(LinkedInRowState.Connected(summary), boards = null, googleSignedIn = false)

        composeRule.onNodeWithTag("profile.linkedin.sign-in-hint").assertDoesNotExist()
        composeRule.onNodeWithTag("profile.linkedin").assertExists()
    }

    @Test
    fun `cloud boards without cloud sync add no hint`() {
        setRow(LinkedInRowState.Connected(summary), LinkedInBoards(savedToAccount = false, canDelete = true), googleSignedIn = false)

        composeRule.onNodeWithTag("profile.linkedin.sign-in-hint").assertDoesNotExist()
    }

    @Test
    fun `a failed delete shows its message under the stats`() {
        setRow(
            LinkedInRowState.Connected(summary),
            LinkedInBoards(savedToAccount = true, canDelete = true, deleteMessage = "You're offline. Try again when you're connected."),
        )

        composeRule.onNodeWithText("You're offline. Try again when you're connected.").assertExists()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }
}
