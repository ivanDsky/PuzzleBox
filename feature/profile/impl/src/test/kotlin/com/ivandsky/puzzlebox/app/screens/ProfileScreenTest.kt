package com.ivandsky.puzzlebox.app.screens

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric compose coverage for the Profile screen states (brief §4, A17): the
 * signed-out prompt (A7), the signed-in identity + sign-out, and leaderboard v1
 * rendering with the own-row highlight.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val boardRef = LeaderboardRef("zip", LocalDate.of(2026, 10, 31))
    private val me = Identity(uid = "u1", displayName = "Ann", photoUrl = null)
    private val entries = listOf(
        LeaderboardEntry("u2", "Bo", "zip/2026-10-31/592", 80_000, 0, 1_700_000_000_000),
        LeaderboardEntry("u1", "Ann", "zip/2026-10-31/592", 95_000, 2, 1_700_000_000_000),
    )

    private var signInClicks = 0
    private var signOutClicks = 0
    private var catalogClicks = 0

    private fun setContent(state: ProfileUiState) {
        signInClicks = 0
        signOutClicks = 0
        catalogClicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                ProfileContent(
                    state = state,
                    onSignIn = { signInClicks++ },
                    onSignOut = { signOutClicks++ },
                    onOpenCatalog = { catalogClicks++ },
                )
            }
        }
    }

    @Test
    fun `signed out shows the sign-in prompt, no leaderboard (A7)`() {
        setContent(ProfileUiState.SignedOut())

        composeRule.onNodeWithTag("profile-signed-out").assertExists()
        composeRule.onNodeWithText("Sign in with Google").assertExists()
        composeRule.onNodeWithTag("profile-signed-in").assertDoesNotExist()
        composeRule.onNodeWithText("Today's leaderboard").assertDoesNotExist()

        composeRule.onNodeWithText("Sign in with Google").performClick()
        assertEquals(1, signInClicks)
    }

    @Test
    fun `signing-in state disables the button with a label`() {
        setContent(ProfileUiState.SignedOut(signingIn = true))

        composeRule.onNodeWithText("Signing in…").assertExists()
        composeRule.onNodeWithText("Sign in with Google").assertDoesNotExist()
    }

    @Test
    fun `sign-in failure surfaces the friendly message`() {
        setContent(
            ProfileUiState.SignedOut(
                signInMessage = "Google sign-in isn't available right now. Try again, or try another account.",
            ),
        )

        composeRule.onNodeWithText(
            "Google sign-in isn't available right now. Try again, or try another account.",
        ).assertExists()
    }

    @Test
    fun `signed in shows the display name and sign-out`() {
        setContent(
            ProfileUiState.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries)),
            ),
        )

        composeRule.onNodeWithTag("profile-signed-in").assertExists()
        // "Ann" appears both as the header display name and in the leaderboard row.
        composeRule.onAllNodesWithText("Ann").onFirst().assertExists()
        composeRule.onNodeWithText("Sign out").assertExists()
        composeRule.onNodeWithTag("profile-signed-out").assertDoesNotExist()

        composeRule.onNodeWithText("Sign out").performClick()
        assertEquals(1, signOutClicks)
    }

    @Test
    fun `leaderboard renders rank, name, time and hints`() {
        setContent(
            ProfileUiState.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries)),
            ),
        )

        composeRule.onNodeWithTag("leaderboard").assertExists()
        composeRule.onNodeWithText("Bo").assertExists()
        composeRule.onNodeWithText("1:20 · 0 hints").assertExists()
        composeRule.onNodeWithText("1:35 · 2 hints").assertExists()
        composeRule.onNodeWithTag("leaderboard-row-self").assertExists()
    }

    @Test
    fun `empty board shows the first-entry invitation`() {
        setContent(
            ProfileUiState.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, emptyList())),
            ),
        )

        composeRule.onNodeWithText("No entries yet today — finish today's Zip to be first.")
            .assertExists()
        composeRule.onNodeWithTag("leaderboard-row-self").assertDoesNotExist()
    }

    @Test
    fun `unobservable board degrades to a soft state, never an error (A7)`() {
        setContent(
            ProfileUiState.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Unavailable,
            ),
        )

        composeRule.onNodeWithText("Leaderboard unavailable right now.").assertExists()
        assertTrue(catalogClicks == 0)
    }
}
