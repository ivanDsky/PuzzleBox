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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ThemeMode
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
    private lateinit var scheme: ColorScheme

    private fun setContent(account: ProfileUiState.Account) = setContent(ProfileUiState(account = account))

    private fun setContent(
        state: ProfileUiState,
        onBack: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) {
        signInClicks = 0
        signOutClicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                scheme = MaterialTheme.colorScheme
                ProfileContent(
                    state = state,
                    onSignIn = { signInClicks++ },
                    onSignOut = { signOutClicks++ },
                    onBack = onBack,
                    onOpenSettings = onOpenSettings,
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
}
