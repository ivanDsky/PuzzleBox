package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric compose tests (A17): pane choice, the Today tiles and the one CTA (A10), the
 * Offline tile (A12), the streak card's week (A8) and the absent theme control (N4), no emulator. Copy
 * is never pinned: tags, enabled state and callbacks only (the CTA is checked to NAME its
 * game, not for its wording).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        layout: HomeLayout,
        history: List<HistoryDay> = emptyList(),
        today: MutableState<List<HomeUiState.TodayRow>> = mutableStateOf(TODAY),
        playTarget: GameId? = GameId("tango"),
        identity: Identity? = null,
        onOpenGame: (GameId) -> Unit = {},
        onOpenProfile: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                HomeScreen(
                    layout = layout,
                    state = state(today.value, history, playTarget, identity),
                    onOpenGame = onOpenGame,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier,
                )
            }
        }
    }

    @Test
    fun `single pane renders home content without a detail pane`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithTag("home.today").assertExists()
        composeRule.onNodeWithTag("home.streak").assertExists()
        composeRule.onNodeWithTag("home.detail_pane").assertDoesNotExist()
    }

    @Test
    fun `two pane renders the detail pane beside the list`() {
        setContent(HomeLayout.TwoPane)

        composeRule.onNodeWithTag("home.today").assertExists()
        composeRule.onNodeWithTag("home.detail_pane").assertExists()
        composeRule.onNodeWithTag("home.detail_today").assertExists()
    }

    @Test
    fun `today lists one shared tile per game`() {
        setContent(HomeLayout.SinglePane)

        TODAY.forEach { composeRule.onNodeWithTag("card.tile.${it.game.value}").assertExists() }
    }

    @Test
    fun `the CTA names the play target and opens it - A10`() {
        val opened = mutableListOf<GameId>()
        setContent(HomeLayout.SinglePane, onOpenGame = { opened += it })

        composeRule.onNodeWithTag("home.today_play")
            .assert(hasText("Tango", substring = true))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("tango")), opened) }
        composeRule.onNodeWithTag("home.today_countdown").assertDoesNotExist()
    }

    @Test
    fun `the two-pane detail CTA names the same target`() {
        val opened = mutableListOf<GameId>()
        setContent(HomeLayout.TwoPane, playTarget = GameId("queens"), onOpenGame = { opened += it })

        composeRule.onNodeWithTag("home.detail_today_play")
            .assert(hasText("Queens", substring = true))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("queens")), opened) }
    }

    @Test
    fun `with every game solved the CTA turns into the countdown - A10`() {
        val solved = TODAY.map { it.copy(status = TodayStatus.Solved(36_000L)) }
        setContent(HomeLayout.SinglePane, today = mutableStateOf(solved), playTarget = GameId("zip"))

        composeRule.onNodeWithTag("home.today_countdown").assertExists()
        composeRule.onNodeWithTag("home.today_play").assertDoesNotExist()
    }

    @Test
    fun `the CTA comes back when a game is no longer solved`() {
        val today = mutableStateOf(TODAY.map { it.copy(status = TodayStatus.Solved(36_000L)) })
        setContent(HomeLayout.SinglePane, today = today)
        composeRule.onNodeWithTag("home.today_countdown").assertExists()

        today.value = TODAY

        composeRule.onNodeWithTag("home.today_play").assertExists()
        composeRule.onNodeWithTag("home.today_countdown").assertDoesNotExist()
    }

    @Test
    fun `a tile opens its game`() {
        val opened = mutableListOf<GameId>()
        setContent(HomeLayout.SinglePane, onOpenGame = { opened += it })

        composeRule.onNodeWithTag("card.tile.queens").performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("queens")), opened) }
    }

    @Test
    fun `an offline tile stays enabled and its tap retries by opening the game - A9`() {
        val opened = mutableListOf<GameId>()
        val today = TODAY.map { if (it.game == GameId("zip")) it.copy(status = TodayStatus.Offline) else it }
        setContent(HomeLayout.SinglePane, today = mutableStateOf(today), onOpenGame = { opened += it })

        TODAY.forEach { composeRule.onNodeWithTag("card.tile.${it.game.value}").assertIsEnabled() }
        composeRule.onNodeWithTag("card.tile.zip").performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), opened) }
    }

    @Test
    fun `the streak card shows the week strip`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithTag("home.week").assertExists()
    }

    @Test
    fun `history teaser shows the empty state when nothing is cached`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithText("No games yet").assertExists()
    }

    @Test
    fun `history rows render a formatted headline per day`() {
        setContent(
            HomeLayout.TwoPane,
            history = listOf(HistoryDay("2026-10-30", "October 30", listOf(HistoryDay.GameResult("Zip", GameBrand.Neutral, "0:42")))),
        )

        composeRule.onNodeWithText("October 30").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }

    @Test
    fun `selecting a two-game day on two pane selects exactly that row`() {
        val twoGames = listOf(HistoryDay.GameResult("Zip", GameBrand.Neutral, "0:42"), HistoryDay.GameResult("Tango", GameBrand.Neutral, null))
        setContent(
            HomeLayout.TwoPane,
            history = listOf(
                HistoryDay("2026-11-03", "November 3", twoGames),
                HistoryDay("2026-11-02", "November 2", twoGames.take(1)),
                HistoryDay("2026-11-01", "November 1", twoGames.takeLast(1)),
            ),
        )

        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.onAllNodes(isSelected()).assertCountEquals(1)
        composeRule.onNodeWithTag("home.history_row_2026-11-03").assertIsSelected()
    }

    @Test
    fun `sign in fires the profile destination`() {
        // Ticket 17 regression: the account card opens Profile (the real account
        // surface) instead of showing placeholder copy.
        var opened = false
        setContent(HomeLayout.SinglePane, onOpenProfile = { opened = true })

        composeRule
            .onNodeWithText("Sign in")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { check(opened) { "expected onOpenProfile" } }
    }

    @Test
    fun `home has no theme control - N4`() {
        setContent(HomeLayout.SinglePane)

        // Appearance moved to Profile (N4): the header carries no gear.
        composeRule.onAllNodesWithContentDescription("Appearance").assertCountEquals(0)
    }

    @Test
    fun `account card reflects signed-out identity as guest`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithText("Guest player").assertExists()
        composeRule.onNodeWithText("Sign in").assertExists()
    }

    @Test
    fun `account card reflects a signed-in identity`() {
        setContent(
            HomeLayout.SinglePane,
            identity = Identity(uid = "u1", displayName = "Ivan D.", photoUrl = null),
        )

        composeRule.onNodeWithText("Ivan D.").assertExists()
        composeRule.onNodeWithText("Competing on leaderboards.").assertExists()
        composeRule.onNodeWithText("Profile").assertExists()
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 4)

        /** Registry order. A loading tile shimmers forever (idling would hang); core:game-ui covers it. */
        val TODAY = listOf(
            row("zip", "Zip", TodayStatus.Solved(36_000L)),
            row("tango", "Tango", TodayStatus.InProgress(42_000L)),
            row("queens", "Queens", TodayStatus.NotStarted),
            row("pinpoint", "Pinpoint", TodayStatus.NotStarted),
        )

        fun row(id: String, name: String, status: TodayStatus?) =
            HomeUiState.TodayRow(GameId(id), name, status, GameBrand.Neutral, RankOrder.TimeThenHints)

        fun state(
            today: List<HomeUiState.TodayRow>,
            history: List<HistoryDay>,
            playTarget: GameId?,
            identity: Identity?,
        ) = HomeUiState(
            today = today,
            identity = identity,
            history = history,
            puzzleDay = DAY,
            playTarget = playTarget,
            stats = GameStats(3, 5, 12, 36_000L, 4, 48_000L, DAY.minusDays(20)),
            week = (6L downTo 0L).map { WeekDay(DAY.minusDays(it), emptyList(), isToday = it == 0L) },
            rolloverAt = Instant.now().plus(3, ChronoUnit.HOURS),
        )
    }
}
