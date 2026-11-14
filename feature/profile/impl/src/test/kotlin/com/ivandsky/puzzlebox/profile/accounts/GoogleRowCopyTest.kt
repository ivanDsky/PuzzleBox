package com.ivandsky.puzzlebox.profile.accounts

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.GoogleSync
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A35: the Google row's sign-in, sign-out and unfinished-games copy with cloud sync on; today's copy without it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class GoogleRowCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val signedIn = ProfileUiState.Account.SignedIn(Identity("u1", "Ann", null))
    private val signedOut = ProfileUiState.Account.SignedOut()

    private fun setRow(account: ProfileUiState.Account, sync: GoogleSync?) {
        val shown = if (account is ProfileUiState.Account.SignedIn) account.copy(sync = sync) else account
        composeRule.setContent {
            PuzzleBoxTheme {
                GoogleRow(
                    account = shown,
                    onSignIn = {},
                    onSignOut = {},
                    onSyncNow = {},
                    onDeleteCloudData = {},
                    nowMs = NOW,
                )
            }
        }
    }

    @Test
    fun `signed out names keeping history on every device`() {
        setRow(signedOut, sync = null)

        composeRule
            .onNodeWithText("Sign in with Google to put your times on the leaderboards and keep your history on every device.")
            .assertExists()
    }

    @Test
    fun `signed in and synced says what stays after sign out and that unfinished games stay on the device`() {
        setRow(signedIn, sync = GoogleSync(GoogleSync.Status.Synced(NOW)))

        composeRule.onNodeWithText("Signing out keeps your history on this phone and in your account.").assertExists()
        composeRule.onNodeWithText("Unfinished games stay on this device.").assertExists()
    }

    @Test
    fun `signed in while the sync engine is signed out shows no sync notes`() {
        setRow(signedIn, sync = null)

        composeRule.onNodeWithText("Signed in with Google").assertExists()
        composeRule.onNodeWithText("Signing out keeps your history on this phone and in your account.").assertDoesNotExist()
        composeRule.onNodeWithText("Unfinished games stay on this device.").assertDoesNotExist()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
