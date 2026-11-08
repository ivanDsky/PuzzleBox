package com.ivandsky.puzzlebox.app.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.profile.board.GameRow
import com.ivandsky.puzzlebox.profile.board.PerGameBoard
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

    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val games = listOf(
        GameRow(zip, "Zip", GameBrand.Neutral, solved = 4, BestScore(41_000, hints = 0), RankOrder.TimeThenHints),
        GameRow(tango, "Tango", GameBrand.Neutral, solved = 0, best = null, RankOrder.TimeThenHints),
    )
    private val me = Identity(uid = "u1", displayName = "Ann", photoUrl = null)

    private var signInClicks = 0
    private var signOutClicks = 0
    private var catalogClicks = 0
    private val openedHistory = mutableListOf<GameId>()
    private val picked = mutableListOf<GameId>()
    private val themes = mutableListOf<ThemeMode>()

    private fun setContent(account: ProfileUiState.Account) = setContent(
        ProfileUiState(
            account = account,
            games = games,
            perGameBoard = PerGameBoard(
                null,
                RankOrder.TimeThenHints,
                if (account is ProfileUiState.Account.SignedIn) DailyBoardState.Loading else DailyBoardState.SignedOut,
            ),
        ),
    )

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
                    onSelectGame = { picked += it },
                    onOpenCatalog = if (showCatalog) ({ catalogClicks++ }) else null,
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
        setContent(ProfileUiState.Account.SignedIn(identity = me))

        composeRule.onNodeWithTag("profile-signed-in").assertExists()
        composeRule.onAllNodesWithText("Ann").onFirst().assertExists()
        composeRule.onNodeWithText("Sign out").assertExists()
        composeRule.onNodeWithTag("profile-signed-out").assertDoesNotExist()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, signOutClicks)
    }

    private val signedIn = ProfileUiState.Account.SignedIn(me)

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
                perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.SignedOut),
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
        setContent(ProfileUiState(games = games, perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.SignedOut)))

        composeRule.onNodeWithTag("profile.header").assertExists()
        composeRule.onNodeWithTag("profile.playing_since").assertDoesNotExist()
    }

    @Test
    fun `appearance shows the current mode and reports a choice - N4`() {
        setContent(ProfileUiState(themeMode = ThemeMode.LIGHT, games = games, perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.SignedOut)))

        composeRule.onNodeWithTag("profile.appearance.light").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("profile.appearance.dark").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf(ThemeMode.DARK), themes) }
    }

    @Test
    fun `the catalog link shows only when offered`() {
        setContent(ProfileUiState(games = games, perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.SignedOut)), showCatalog = false)

        composeRule.onNodeWithTag("profile.catalog").assertDoesNotExist()
    }

    @Test
    fun `shows a chip per game over the shared board and reports a pick`() {
        val rows = listOf(DailyBoardRowModel(1, "Bo", 80_000, 0, isSelf = false))
        setContent(
            ProfileUiState(
                account = signedIn,
                games = games,
                perGameBoard = PerGameBoard(zip, RankOrder.TimeThenHints, DailyBoardState.Ranked(rows)),
            ),
        )

        composeRule.onNodeWithTag("profile-board-chip-zip").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("profile-board-chip-tango").assertExists()
        composeRule.onNodeWithText("Zip · today", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Bo").assertExists()
        composeRule.onNodeWithTag("leaderboard").assertDoesNotExist()

        composeRule.onNodeWithTag("profile-board-chip-tango").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(listOf(tango), picked) }
    }

    /** Like [setContent] but 2000dp wide, so every chip composes in the LazyRow. */
    private fun setContentWide(state: ProfileUiState, showCatalog: Boolean = true) {
        signInClicks = 0
        signOutClicks = 0
        catalogClicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                // Wider than any real window so every chip composes in the LazyRow
                // and left-to-right x order is directly observable.
                Box(Modifier.requiredWidth(2000.dp)) {
                    ProfileContent(
                        state = state,
                        onSignIn = { signInClicks++ },
                        onSignOut = { signOutClicks++ },
                        onSetThemeMode = { themes += it },
                        onOpenHistory = { openedHistory += it },
                        onSelectGame = { picked += it },
                        onOpenCatalog = if (showCatalog) ({ catalogClicks++ }) else null,
                    )
                }
            }
        }
    }

    /** A2: the chips render in the game order (zip, tango, queens, strands, minisudoku). */
    @Test
    fun `shows the chips in game order`() {
        val queens = GameId("queens")
        val strands = GameId("strands")
        val minisudoku = GameId("minisudoku")
        val allGames = games + listOf(
            GameRow(queens, "Queens", GameBrand.Neutral, solved = 0, best = null, RankOrder.TimeThenHints),
            GameRow(strands, "Strands", GameBrand.Neutral, solved = 0, best = null, RankOrder.TimeThenHints),
            GameRow(minisudoku, "MiniSudoku", GameBrand.Neutral, solved = 0, best = null, RankOrder.TimeThenHints),
        )
        setContentWide(
            ProfileUiState(
                account = signedIn,
                games = allGames,
                perGameBoard = PerGameBoard(zip, RankOrder.TimeThenHints, DailyBoardState.Loading),
            ),
        )

        var lastX = Float.NEGATIVE_INFINITY
        for (id in listOf("zip", "tango", "queens", "strands", "minisudoku")) {
            val x = composeRule.onNodeWithTag("profile-board-chip-$id")
                .fetchSemanticsNode()
                .positionInRoot.x
            assertTrue("chip $id (x=$x) must sit right of the previous chip (x=$lastX)", x > lastX)
            lastX = x
        }
    }

    @Test
    fun `with the default pending shows Loading under no selected chip`() {
        setContent(
            ProfileUiState(
                account = signedIn,
                games = games,
                perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.Loading),
            ),
        )

        composeRule.onNodeWithText("Loading…").assertExists()
        composeRule.onNodeWithTag("profile-board-chip-zip").performScrollTo().assertIsNotSelected()
    }

    @Test
    fun `signed out keeps the sign-in card, says leaderboards, no switcher`() {
        setContent(
            ProfileUiState(
                games = games,
                perGameBoard = PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.SignedOut),
            ),
        )

        composeRule.onNodeWithText("Sign in with Google to put your times on the leaderboards.").assertExists()
        composeRule.onNodeWithTag("profile-board-chip-zip").assertDoesNotExist()
    }

}
