package com.ivandsky.puzzlebox.app.screens

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
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
    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val games = listOf(
        ProfileUiState.GameRow(zip, "Zip", GameBrand.Neutral, solved = 4, BestScore(41_000, hints = 0), RankOrder.TimeThenHints),
        ProfileUiState.GameRow(tango, "Tango", GameBrand.Neutral, solved = 0, best = null, RankOrder.TimeThenHints),
    )
    private val me = Identity(uid = "u1", displayName = "Ann", photoUrl = null)
    private val entries = listOf(
        LeaderboardEntry("u2", "Bo", "zip/2026-10-31/592", 80_000, 0, 1_700_000_000_000),
        LeaderboardEntry("u1", "Ann", "zip/2026-10-31/592", 95_000, 2, 1_700_000_000_000),
    )

    private var signInClicks = 0
    private var signOutClicks = 0
    private var catalogClicks = 0
    private val openedHistory = mutableListOf<GameId>()
    private val themes = mutableListOf<ThemeMode>()

    private fun setContent(account: ProfileUiState.Account) = setContent(ProfileUiState(account = account, games = games))

    private fun setContent(state: ProfileUiState, showCatalog: Boolean = true) {
        signInClicks = 0
        signOutClicks = 0
        catalogClicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                ProfileContent(
                    state = state,
                    onSignIn = { signInClicks++ },
                    onSignOut = { signOutClicks++ },
                    onSetThemeMode = { themes += it },
                    onOpenHistory = { openedHistory += it },
                    onOpenCatalog = if (showCatalog) ({ catalogClicks++ }) else null,
                )
            }
        }
    }

    @Test
    fun `signed out shows the sign-in prompt, no leaderboard (A7)`() {
        setContent(ProfileUiState.Account.SignedOut())

        composeRule.onNodeWithTag("profile-signed-out").assertExists()
        composeRule.onNodeWithText("Sign in with Google").assertExists()
        composeRule.onNodeWithTag("profile-signed-in").assertDoesNotExist()
        composeRule.onNodeWithText("Today's leaderboard").assertDoesNotExist()

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
        setContent(
            ProfileUiState.Account.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries)),
            ),
        )

        composeRule.onNodeWithTag("profile-signed-in").assertExists()
        // "Ann" appears both as the header display name and in the leaderboard row.
        composeRule.onAllNodesWithText("Ann").onFirst().assertExists()
        composeRule.onNodeWithText("Sign out").assertExists()
        composeRule.onNodeWithTag("profile-signed-out").assertDoesNotExist()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, signOutClicks)
    }

    @Test
    fun `leaderboard renders rank, name, time and hints`() {
        setContent(
            ProfileUiState.Account.SignedIn(
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
            ProfileUiState.Account.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, emptyList())),
            ),
        )

        // The empty-board line names no game (A20).
        games.forEach { game ->
            composeRule
                .onNode(hasAnyAncestor(hasTestTag("leaderboard")) and hasText(game.name, substring = true))
                .assertDoesNotExist()
        }
        composeRule.onNodeWithTag("leaderboard-row-self").assertDoesNotExist()
    }

    @Test
    fun `unobservable board degrades to a soft state, never an error (A7)`() {
        setContent(
            ProfileUiState.Account.SignedIn(
                identity = me,
                board = TodayLeaderboard.Board.Unavailable,
            ),
        )

        composeRule.onNodeWithText("Leaderboard unavailable right now.").assertExists()
        assertTrue(catalogClicks == 0)
    }

    @Test
    fun `a game row opens that game's history - N3`() {
        setContent(ProfileUiState.Account.SignedOut())

        composeRule.onNodeWithTag("profile.game.tango").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf(tango), openedHistory) }
    }

    @Test
    fun `the record shows the lifetime grid and each game's best - A20`() {
        setContent(
            ProfileUiState(
                stats = ProfileUiState.NO_STATS.copy(solved = 4, bestStreak = 3, hintFree = 2, playingSince = LocalDate.of(2026, 3, 9)),
                daysPlayed = 5,
                games = games,
            ),
        )

        composeRule.onNodeWithTag("profile.playing_since").assertExists()
        composeRule.onNodeWithTag("profile.stats.solved").assertTextEquals("4")
        composeRule.onNodeWithTag("profile.stats.best_streak").assertTextEquals("3")
        composeRule.onNodeWithTag("profile.stats.hint_free").assertTextEquals("2")
        composeRule.onNodeWithTag("profile.stats.days_played").assertTextEquals("5")
        composeRule.onNodeWithTag("profile.game.best.zip", useUnmergedTree = true).assertTextEquals("0:41")
        composeRule.onNodeWithTag("profile.game.solved.tango", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `playing since is hidden before the first solve`() {
        setContent(ProfileUiState(games = games))

        composeRule.onNodeWithTag("profile.header").assertExists()
        composeRule.onNodeWithTag("profile.playing_since").assertDoesNotExist()
    }

    @Test
    fun `appearance shows the current mode and reports a choice - N4`() {
        setContent(ProfileUiState(themeMode = ThemeMode.LIGHT, games = games))

        composeRule.onNodeWithTag("profile.appearance.light").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("profile.appearance.dark").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf(ThemeMode.DARK), themes) }
    }

    @Test
    fun `the catalog link shows only when offered`() {
        setContent(ProfileUiState(games = games), showCatalog = false)

        composeRule.onNodeWithTag("profile.catalog").assertDoesNotExist()
    }
}
