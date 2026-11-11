package com.ivandsky.puzzlebox.app.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import com.ivandsky.puzzlebox.profile.settings.SettingsScreen
import com.ivandsky.puzzlebox.profile.settings.SettingsUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric compose coverage for the Profile screen states (brief §4, A17): the
 * signed-out prompt (A7), the signed-in identity + sign-out, the header, and the top bar's
 * back icon and gear.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val me = Identity(uid = "u1", displayName = "Ann", photoUrl = null)
    private val signedIn = ProfileUiState.Account.SignedIn(me)

    private var signInClicks = 0
    private var signOutClicks = 0
    private val linkedInTaps = mutableListOf<String>()
    private lateinit var scheme: ColorScheme

    private fun setContent(account: ProfileUiState.Account) = setContent(ProfileUiState(account = account))

    private fun setContent(
        state: ProfileUiState,
        onBack: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        linkedInOn: Boolean = false,
    ) {
        signInClicks = 0
        signOutClicks = 0
        linkedInTaps.clear()
        composeRule.setContent {
            PuzzleBoxTheme {
                scheme = MaterialTheme.colorScheme
                ProfileContent(
                    state = state,
                    onSignIn = { signInClicks++ },
                    onSignOut = { signOutClicks++ },
                    onBack = onBack,
                    onOpenSettings = onOpenSettings,
                    nowMs = NOW,
                    onConnectLinkedIn = if (linkedInOn) ({ linkedInTaps += "connect" }) else null,
                    onResumeLinkedIn = { linkedInTaps += "resume" },
                    onDisconnectLinkedIn = { linkedInTaps += "disconnect" },
                )
            }
        }
    }

    @Test
    fun `signed out shows the sign-in prompt (A7)`() {
        setContent(ProfileUiState.Account.SignedOut())

        composeRule.onNodeWithTag("profile-signed-out").assertExists()
        composeRule.onNodeWithText("Sign in with Google").assertExists()
        composeRule.onNodeWithTag("profile-signed-in").assertDoesNotExist()
        composeRule.onNodeWithText("Sign in with Google to put your times on the leaderboards.").assertExists()

        composeRule.onNodeWithText("Sign in with Google").performScrollTo().performClick()
        assertEquals(1, signInClicks)
    }

    @Test
    fun `signing-in state disables the button with a label`() {
        setContent(ProfileUiState.Account.SignedOut(signingIn = true))

        composeRule.onNodeWithText("Signing in…").assertExists()
        composeRule.onNodeWithText("Sign in with Google").assertDoesNotExist()
    }

    @Test
    fun `sign-in failure surfaces the friendly message`() {
        setContent(
            ProfileUiState.Account.SignedOut(
                signInMessage = "Google sign-in isn't available right now. Try again, or try another account.",
            ),
        )

        composeRule.onNodeWithText(
            "Google sign-in isn't available right now. Try again, or try another account.",
        ).assertExists()
    }

    @Test
    fun `signed in shows the display name and sign-out`() {
        setContent(signedIn)

        composeRule.onNodeWithTag("profile-signed-in").assertExists()
        composeRule.onAllNodesWithText("Ann").onFirst().assertExists()
        composeRule.onNodeWithText("Sign out").assertExists()
        composeRule.onNodeWithTag("profile-signed-out").assertDoesNotExist()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, signOutClicks)
    }

    @Test
    fun `playing since shows once there is a first solve`() {
        setContent(ProfileUiState(playingSince = LocalDate.of(2026, 3, 9)))

        composeRule.onNodeWithTag("profile.playing_since").assertExists()
    }

    @Test
    fun `playing since is hidden before the first solve`() {
        setContent(ProfileUiState())

        composeRule.onNodeWithTag("profile.header").assertExists()
        composeRule.onNodeWithTag("profile.playing_since").assertDoesNotExist()
    }

    @Test
    fun `signed out shows the header and sign-in`() {
        setContent(ProfileUiState.Account.SignedOut())

        composeRule.onNodeWithTag("profile.header").assertExists()
        composeRule.onNodeWithTag("profile-signed-out").assertExists()
    }

    @Test
    fun `the back icon calls the back callback once`() {
        var backs = 0
        setContent(ProfileUiState(account = signedIn), onBack = { backs++ })

        composeRule.onNodeWithTag("profile.back").performClick()

        composeRule.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun `the gear calls the settings callback once`() {
        var opened = 0
        setContent(ProfileUiState(account = signedIn), onOpenSettings = { opened++ })

        composeRule.onNodeWithTag("profile.settings").performClick()

        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    /** The avatar disc's fill, read at its left edge: inside the circle, clear of the initial. */
    private fun avatarFill(): Color {
        val pixels = composeRule.onNodeWithTag("profile.avatar").captureToImage().toPixelMap()
        return pixels[pixels.width * 15 / 100, pixels.height / 2]
    }

    @Test
    fun `signed in the avatar sits on the player's own tone`() {
        setContent(signedIn)
        assertEquals(scheme.primaryContainer.toArgb(), avatarFill().toArgb())
    }

    @Test
    fun `signed out the avatar is the neutral placeholder`() {
        setContent(ProfileUiState.Account.SignedOut())
        assertEquals(scheme.surfaceContainerHighest.toArgb(), avatarFill().toArgb())
    }

    @Test
    fun `under its top bar profile's content starts where settings' does`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        ProfileContent(
                            state = ProfileUiState(account = signedIn),
                            onSignIn = {},
                            onSignOut = {},
                            onBack = {},
                            onOpenSettings = {},
                            nowMs = NOW,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        SettingsScreen(
                            state = SettingsUiState(themeMode = ThemeMode.LIGHT, versionName = "1.4.0"),
                            onSetThemeMode = {},
                            onSetQueensAutoCross = {},
                            onSetSudokuPeerHighlight = {},
                            onSetSudokuSameDigitHighlight = {},
                            onBack = {},
                            onOpenCatalog = null,
                        )
                    }
                }
            }
        }

        val profileTop = composeRule.onNodeWithTag("profile.header").getUnclippedBoundsInRoot().top
        val settingsTop = composeRule.onNodeWithTag("settings.appearance").getUnclippedBoundsInRoot().top
        assertEquals("profile's content top vs settings' under the same bar", settingsTop, profileTop)
    }

    // ---- Accounts: the Google row keeps what the old sections did ---------------------

    @Test
    fun `the accounts list titles itself and holds the Google row`() {
        setContent(signedIn)

        composeRule.onNodeWithText("Accounts").assertExists()
        composeRule.onNodeWithText("Google").assertExists()
        composeRule.onNodeWithText("Signed in with Google").assertExists()
    }

    @Test
    fun `the sign-in failure message sits under the sign-in button inside the Google row`() {
        setContent(ProfileUiState.Account.SignedOut(signInMessage = "Sign-in failed. Please try again."))

        composeRule.onNodeWithTag("profile-signed-out").assertExists()
        composeRule.onNodeWithText("Sign-in failed. Please try again.").assertExists()
    }

    @Test
    fun `signing in disables the Google button`() {
        setContent(ProfileUiState.Account.SignedOut(signingIn = true))

        composeRule.onNodeWithText("Signing in…").assertIsNotEnabled()
    }

    // ---- LinkedIn row ---------------------------------------------------------------

    private val known = LinkedInAccountView(
        name = "Mira Voss", photoPath = null, connectionsSeen = 142, daysThisWeek = 5, daysTotal = 213,
        games = listOf("Queens", "Tango", "Zip"), updatedAtMs = NOW - 12 * MINUTE,
    )
    private val unknown = LinkedInAccountView(null, null, 0, 0, 0, emptyList(), null)

    private fun setLinkedIn(row: LinkedInRowState?, account: ProfileUiState.Account = signedIn) =
        setContent(ProfileUiState(account = account, linkedIn = row), linkedInOn = true)

    @Test
    fun `with the flag off no LinkedIn row is drawn even when the state has one`() {
        setContent(ProfileUiState(account = signedIn, linkedIn = LinkedInRowState.NotConnected), linkedInOn = false)

        composeRule.onNodeWithTag("profile.linkedin").assertDoesNotExist()
        composeRule.onNodeWithText("Connect LinkedIn").assertDoesNotExist()
    }

    @Test
    fun `not connected offers Connect LinkedIn and tapping it opens login once`() {
        setLinkedIn(LinkedInRowState.NotConnected)

        composeRule.onNodeWithText("LinkedIn").assertExists()
        composeRule.onNodeWithText(
            "Import your own LinkedIn game results and see your connections' times on this phone.",
        ).assertExists()
        composeRule.onNodeWithTag("profile.linkedin.overflow").assertDoesNotExist()

        composeRule.onNodeWithText("Connect LinkedIn").performScrollTo().performClick()

        assertEquals(listOf("connect"), linkedInTaps)
    }

    @Test
    fun `the LinkedIn row is the same whether Google is signed in or out`() {
        setLinkedIn(LinkedInRowState.NotConnected, account = ProfileUiState.Account.SignedOut())

        composeRule.onNodeWithText("Connect LinkedIn").assertExists()
        composeRule.onNodeWithText("Sign in with Google").assertExists()
    }

    @Test
    fun `connected with a name shows the name, the counts and the update time`() {
        setLinkedIn(LinkedInRowState.Connected(known))

        composeRule.onNodeWithText("Mira Voss").assertExists()
        composeRule.onNodeWithText("142 connections seen · 5 days this week").assertExists()
        composeRule.onNodeWithText("213 days total · Queens, Tango, Zip · Updated 12 min ago").assertExists()
        composeRule.onNodeWithTag("profile.linkedin.connect").assertDoesNotExist()
    }

    @Test
    fun `connected before the first update says the name is on its way and never doubles Updated`() {
        setLinkedIn(LinkedInRowState.Connected(unknown))

        composeRule.onNodeWithText("LinkedIn account").assertExists()
        composeRule.onNodeWithText("Name and photo appear after the first update").assertExists()
        composeRule.onNodeWithText("0 days total · no games yet · Updating…").assertExists()
    }

    @Test
    fun `disconnect is in the overflow, acts at once and the menu closes`() {
        setLinkedIn(LinkedInRowState.Connected(known))

        composeRule.onNodeWithContentDescription("LinkedIn options").performScrollTo().performClick()
        composeRule.onNodeWithText("Disconnect LinkedIn").performClick()

        assertEquals(listOf("disconnect"), linkedInTaps)
        composeRule.onNodeWithText("Disconnect LinkedIn").assertDoesNotExist()
    }

    @Test
    fun `paused shows the reason in the sub, Resume and the overflow`() {
        setLinkedIn(LinkedInRowState.Paused(known, "LinkedIn is busy.", needsReconnect = false))

        composeRule.onNodeWithText("Paused · LinkedIn is busy.").assertExists()
        composeRule.onNodeWithText("213 days total · Queens, Tango, Zip · Updated 12 min ago").assertExists()
        composeRule.onNodeWithTag("profile.linkedin.overflow").assertExists()

        composeRule.onNodeWithText("Resume").performScrollTo().performClick()

        assertEquals(listOf("resume"), linkedInTaps)
    }

    @Test
    fun `needing a reconnect shows Reconnect with no Resume and no stats`() {
        setLinkedIn(LinkedInRowState.Paused(known, "Your LinkedIn login expired.", needsReconnect = true))

        composeRule.onNodeWithText("LinkedIn signed this phone out.").assertExists()
        composeRule.onNodeWithText("Resume").assertDoesNotExist()
        composeRule.onNodeWithText("213 days total · Queens, Tango, Zip · Updated 12 min ago").assertDoesNotExist()
        composeRule.onNodeWithTag("profile.linkedin.overflow").assertExists()

        composeRule.onNodeWithText("Reconnect").performScrollTo().performClick()

        assertEquals(listOf("connect"), linkedInTaps)
    }

    @Test
    fun `a long LinkedIn name stays on one line`() {
        val long = known.copy(name = "Mira Voss-Halloran Åkerström-Lindqvist the Third of Nowhere in Particular")
        setLinkedIn(LinkedInRowState.Connected(long))

        val title = composeRule.onNodeWithText(long.name!!).getUnclippedBoundsInRoot()
        val sub = composeRule.onNodeWithText("142 connections seen · 5 days this week").getUnclippedBoundsInRoot()
        assertEquals("one line for the name", sub.top - title.top < 40.dp, true)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }
}
