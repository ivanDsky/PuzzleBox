package com.ivandsky.puzzlebox.profile.accounts

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.GoogleSync
import com.ivandsky.puzzlebox.data.cloud.PauseReason
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Semantic coverage of the Google row's sync states (A32): each line's copy, Sync now, and the confirmed delete. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class GoogleSyncLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val signedIn = ProfileUiState.Account.SignedIn(Identity("u1", "Ann", null))
    private var syncNowClicks = 0
    private var deleteClicks = 0
    private var signOutClicks = 0

    private fun setRow(sync: GoogleSync?, account: ProfileUiState.Account = signedIn) {
        syncNowClicks = 0
        deleteClicks = 0
        signOutClicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                GoogleRow(
                    account = account,
                    onSignIn = {},
                    onSignOut = { signOutClicks++ },
                    sync = sync,
                    nowMs = NOW,
                    onSyncNow = { syncNowClicks++ },
                    onDeleteCloudData = { deleteClicks++ },
                )
            }
        }
    }

    @Test
    fun `synced shows the house age phrase`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW - 2 * MINUTE)))

        composeRule.onNodeWithText("Synced · 2 min ago").assertExists()
        composeRule.onNodeWithText("Sync now").assertIsEnabled()
    }

    @Test
    fun `synced a moment ago reads just now`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW)))

        composeRule.onNodeWithText("Synced · just now").assertExists()
    }

    @Test
    fun `syncing says so and leaves Sync now idle`() {
        setRow(GoogleSync(GoogleSync.Status.Syncing))

        composeRule.onNodeWithText("Syncing…").assertExists()
        composeRule.onNodeWithTag("profile.google.sync.now").assertIsNotEnabled()
    }

    @Test
    fun `offline says history did not sync and will when online`() {
        setRow(GoogleSync(GoogleSync.Status.Offline))

        composeRule.onNodeWithText("No connection, so your history didn't sync. It will when you're online.").assertExists()
        composeRule.onNodeWithText("Sync now").assertIsEnabled()
    }

    @Test
    fun `paused gives each reason its own short line`() {
        val lines = mapOf(
            PauseReason.Exhausted to "Sync paused · over today's limit, tries again later",
            PauseReason.WriteCap to "Sync paused · this phone is over its daily limit, tries again later",
            PauseReason.Denied to "Sync paused · your account was refused, tries again later",
        )
        assertEquals(PauseReason.entries.toSet(), lines.keys)
        val sync = mutableStateOf(GoogleSync(GoogleSync.Status.Paused(PauseReason.Exhausted)))
        composeRule.setContent {
            PuzzleBoxTheme { GoogleRow(account = signedIn, onSignIn = {}, onSignOut = {}, sync = sync.value, nowMs = NOW) }
        }

        lines.forEach { (reason, line) ->
            sync.value = GoogleSync(GoogleSync.Status.Paused(reason))

            composeRule.onNodeWithText(line).assertExists()
            composeRule.onNodeWithText("Sync now").assertIsEnabled()
        }
    }

    @Test
    fun `erasing shows Deleting and leaves Sync now idle`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW), erasing = true))

        composeRule.onNodeWithText("Deleting…").assertExists()
        composeRule.onNodeWithText("Synced · just now").assertDoesNotExist()
        composeRule.onNodeWithTag("profile.google.sync.now").assertIsNotEnabled()
    }

    @Test
    fun `the open delete confirm survives a rotation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            PuzzleBoxTheme {
                GoogleRow(account = signedIn, onSignIn = {}, onSignOut = {}, sync = GoogleSync(GoogleSync.Status.Synced(NOW)), nowMs = NOW)
            }
        }
        composeRule.onNodeWithTag("profile.google.overflow").performClick()
        composeRule.onNodeWithText("Delete my cloud data").performClick()
        composeRule.onNodeWithTag("profile.google.delete.dialog").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("profile.google.delete.dialog").assertExists()
    }

    @Test
    fun `sync now reports a tap`() {
        setRow(GoogleSync(GoogleSync.Status.Offline))

        composeRule.onNodeWithText("Sync now").performClick()

        assertEquals(1, syncNowClicks)
    }

    @Test
    fun `an erase message shows under the line`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW), eraseMessage = "You're offline. Try again when you're connected."))

        composeRule.onNodeWithText("You're offline. Try again when you're connected.").assertExists()
    }

    @Test
    fun `delete my cloud data asks first and calls only on Delete`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW)))
        composeRule.onNodeWithTag("profile.google.delete.dialog").assertDoesNotExist()

        composeRule.onNodeWithTag("profile.google.overflow").performClick()
        composeRule.onNodeWithText("Delete my cloud data").performClick()
        composeRule.onNodeWithTag("profile.google.delete.dialog").assertExists()
        composeRule.onNodeWithText("Delete my cloud data?").assertExists()
        assertEquals(0, deleteClicks)

        composeRule.onNodeWithTag("profile.google.delete.confirm").performClick()

        assertEquals(1, deleteClicks)
        composeRule.onNodeWithTag("profile.google.delete.dialog").assertDoesNotExist()
    }

    @Test
    fun `cancelling the confirm deletes nothing`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW)))

        composeRule.onNodeWithTag("profile.google.overflow").performClick()
        composeRule.onNodeWithText("Delete my cloud data").performClick()
        composeRule.onNodeWithTag("profile.google.delete.cancel").performClick()

        assertEquals(0, deleteClicks)
        composeRule.onNodeWithTag("profile.google.delete.dialog").assertDoesNotExist()
    }

    @Test
    fun `delete is not offered while one is running`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW), erasing = true))

        composeRule.onNodeWithTag("profile.google.overflow").performClick()

        composeRule.onNodeWithTag("profile.google.delete").assertIsNotEnabled()
    }

    @Test
    fun `without cloud sync the row is the plain one`() {
        setRow(sync = null)

        composeRule.onNodeWithText("Sign out").assertExists()
        composeRule.onNodeWithTag("profile.google.sync").assertDoesNotExist()
        composeRule.onNodeWithTag("profile.google.overflow").assertDoesNotExist()
        composeRule.onNodeWithText("Sync now").assertDoesNotExist()

        composeRule.onNodeWithText("Sign out").performClick()
        assertEquals(1, signOutClicks)
    }

    @Test
    fun `signed out shows no sync even when given one`() {
        setRow(GoogleSync(GoogleSync.Status.Synced(NOW)), account = ProfileUiState.Account.SignedOut())

        composeRule.onNodeWithTag("profile.google.sync").assertDoesNotExist()
        composeRule.onNodeWithText("Sign in with Google").assertExists()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }
}
