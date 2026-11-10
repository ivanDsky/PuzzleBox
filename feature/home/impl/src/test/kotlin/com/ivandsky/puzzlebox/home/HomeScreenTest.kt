package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.identity.Identity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric compose tests (A17): pane choice, the Today summary card's badges and the one CTA (A10), the
 * Offline badge (A12), the streak card's week (A8) and the absent theme control (N4), no emulator. Copy
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
        reselect: SharedFlow<Unit> = MutableSharedFlow(),
        hideOwnedElsewhere: Boolean = false,
        rank: TodayRank? = null,
        summaries: HomeSummaries? = null,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides reselect) {
                PuzzleBoxTheme {
                    HomeScreen(
                        layout = layout,
                        state = state(today.value, history, playTarget, identity).copy(rank = rank),
                        onOpenGame = onOpenGame,
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier,
                        hideOwnedElsewhere = hideOwnedElsewhere,
                        summaries = summaries,
                    )
                }
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
    fun `two pane with no day selected shows streak and stats beside the list`() {
        setContent(HomeLayout.TwoPane)

        composeRule.onNodeWithTag("home.today").assertExists()
        composeRule.onNodeWithTag("home.detail_pane").assertExists()
        composeRule.onNodeWithTag("home.streak_pane").assertExists()
        composeRule.onNodeWithTag("home.pane_week").assertExists()
        listOf("solved", "hint_free", "average", "since").forEach {
            composeRule.onNodeWithTag("home.stat_$it", useUnmergedTree = true).assertExists()
        }
        TODAY.forEach { composeRule.onNodeWithTag("home.best.${it.game.value}", useUnmergedTree = true).assertExists() }
        composeRule.onNodeWithTag("home.day_detail").assertDoesNotExist()
    }

    @Test
    fun `two pane list drops the streak card and has one copy of today`() {
        setContent(HomeLayout.TwoPane)

        composeRule.onNodeWithTag("home.streak").assertDoesNotExist()
        composeRule.onNodeWithTag("home.week").assertDoesNotExist()
        composeRule.onNodeWithTag("home.badge.zip").assertExists()
        composeRule.onNodeWithTag("home.detail_badge.zip").assertDoesNotExist()
    }

    @Test
    fun `tapping a recent day swaps the pane to that day and away from the stats`() {
        setContent(
            HomeLayout.TwoPane,
            history = listOf(HistoryDay("2026-11-03", "November 3", listOf(HistoryDay.GameResult("Zip", GameBrand.Neutral, "0:42")))),
        )

        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()

        composeRule.onNodeWithTag("home.day_detail").assertExists()
        composeRule.onNodeWithTag("home.streak_pane").assertDoesNotExist()
    }

    @Test
    fun `a picked day rises over the stats pane, which holds still beneath it - polish 04`() {
        setContent(
            HomeLayout.TwoPane,
            history = listOf(HistoryDay("2026-11-03", "November 3", listOf(HistoryDay.GameResult("Zip", GameBrand.Neutral, "0:42")))),
        )
        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo()
        composeRule.waitForIdle()
        val rest = composeRule.onNodeWithTag("home.streak_pane").getUnclippedBoundsInRoot().top

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()
        // A paused clock never idles the looper, so the platform's deferred snapshot apply
        // would not run; apply the pick ourselves or the swap is never seen.
        composeRule.runOnIdle { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeBy(MotionTokens.MixTabIn / 3L)

        assertEquals("the stats hold still", rest, composeRule.onNodeWithTag("home.streak_pane").getUnclippedBoundsInRoot().top)
        val paneTop = composeRule.onNodeWithTag("home.detail_pane").getUnclippedBoundsInRoot().top
        val risen = composeRule.onNodeWithTag("home.day_detail").getUnclippedBoundsInRoot().top
        assertTrue("the day is still rising: $risen vs $paneTop", risen > paneTop)

        composeRule.mainClock.advanceTimeBy(MotionTokens.MixTabIn.toLong() * 4)
        composeRule.onNodeWithTag("home.streak_pane").assertDoesNotExist()
    }

    @Test
    fun `today lists one badge per game in the summary card`() {
        setContent(HomeLayout.SinglePane)

        composeRule.onNodeWithTag("home.today_summary").assertExists()
        TODAY.forEach { composeRule.onNodeWithTag("home.badge.${it.game.value}").assertExists() }
    }

    @Test
    fun `an all-new set offers to play the first game`() {
        val fresh = TODAY.map { it.copy(status = TodayStatus.NotStarted) }
        setContent(HomeLayout.SinglePane, today = mutableStateOf(fresh), playTarget = GameId("zip"))

        composeRule.onNodeWithTag("home.today_summary").assertExists()
        composeRule.onNodeWithTag("home.today_play").assert(hasText("Zip", substring = true))
        composeRule.onNodeWithTag("home.today_countdown").assertDoesNotExist()
    }

    @Test
    fun `while the set loads the card shows skeletons and keeps the CTA`() {
        // Skeleton sheens loop forever: a free-running clock would never idle.
        composeRule.mainClock.autoAdvance = false
        val loading = TODAY.map { it.copy(status = null) }
        setContent(HomeLayout.SinglePane, today = mutableStateOf(loading), playTarget = GameId("zip"))
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag("home.today_loading").assertExists()
        composeRule.onNodeWithTag("home.today_summary").assertDoesNotExist()
        TODAY.forEach { composeRule.onNodeWithTag("home.badge.${it.game.value}").assertExists() }
        composeRule.onNodeWithTag("home.today_play").assertExists()
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
    fun `a badge opens its game`() {
        val opened = mutableListOf<GameId>()
        setContent(HomeLayout.SinglePane, onOpenGame = { opened += it })

        composeRule.onNodeWithTag("home.badge.queens").performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("queens")), opened) }
    }

    @Test
    fun `an in-progress next game keeps the CTA on it`() {
        val opened = mutableListOf<GameId>()
        setContent(HomeLayout.SinglePane, onOpenGame = { opened += it })

        composeRule.onNodeWithTag("home.today_play").assert(hasText("Tango", substring = true)).performScrollTo().performClick()
        composeRule.onNodeWithTag("home.badge.tango").performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("tango"), GameId("tango")), opened) }
    }

    @Test
    fun `an offline badge stays enabled and its tap retries by opening the game - A9`() {
        val opened = mutableListOf<GameId>()
        val today = TODAY.map { if (it.game == GameId("zip")) it.copy(status = TodayStatus.Offline) else it }
        setContent(HomeLayout.SinglePane, today = mutableStateOf(today), onOpenGame = { opened += it })

        TODAY.forEach { composeRule.onNodeWithTag("home.badge.${it.game.value}").assertIsEnabled() }
        composeRule.onNodeWithTag("home.badge.zip").performClick()

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

    @Test
    fun `a tab reselect scrolls the list back to its header`() {
        val reselect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        setContent(HomeLayout.SinglePane, reselect = reselect)
        composeRule.onNodeWithText("Guest player").performScrollTo()
        composeRule.onNodeWithText("PuzzleBox").assertIsNotDisplayed()

        composeRule.runOnIdle { reselect.tryEmit(Unit) }

        composeRule.onNodeWithText("PuzzleBox").assertIsDisplayed()
    }

    @Test
    fun `phone with owned-elsewhere hidden keeps recent days and drops the account card`() {
        val day = HistoryDay("2026-11-03", "November 3", listOf(HistoryDay.GameResult("Zip", GameBrand.Neutral, "0:42")))
        setContent(HomeLayout.SinglePane, history = listOf(day), hideOwnedElsewhere = true)

        composeRule.onNodeWithTag("home.history_row_2026-11-03").assertExists()
        composeRule.onNodeWithText("Sign in").assertDoesNotExist()
        composeRule.onNodeWithTag("home.today").assertExists()
        composeRule.onNodeWithTag("home.streak").assertExists()
    }

    @Test
    fun `tablet with owned-elsewhere hidden shows a tapped day's detail and hides the bests`() {
        val day = HistoryDay("2026-11-03", "November 3", listOf(HistoryDay.GameResult("Zip", GameBrand.Neutral, "0:42")))
        setContent(HomeLayout.TwoPane, history = listOf(day), hideOwnedElsewhere = true)

        composeRule.onNodeWithTag("home.streak_pane").assertExists()
        TODAY.forEach { composeRule.onNodeWithTag("home.best.${it.game.value}", useUnmergedTree = true).assertDoesNotExist() }
        composeRule.onNodeWithTag("home.history_row_2026-11-03").performScrollTo().performClick()
        composeRule.onNodeWithTag("home.day_detail").assertExists()
    }

    private val zipRank = TodayRank(GameId("zip"), "Zip", 12)

    @Test
    fun `rank row shows the rank and reports its game`() {
        var opened: GameId? = null
        setContent(
            HomeLayout.SinglePane,
            rank = zipRank,
            summaries = HomeSummaries(onOpenRank = { opened = it }, onOpenDuels = null, duelsNeedYou = null),
        )

        composeRule.onNodeWithTag("summary.leaderboard").performScrollTo().assert(hasText("Today's rank: #12 on Zip"))
        composeRule.onNodeWithTag("summary.leaderboard").performClick()

        assertEquals(GameId("zip"), opened)
    }

    @Test
    fun `rank row is absent without a callback`() {
        setContent(HomeLayout.SinglePane, rank = zipRank, summaries = HomeSummaries(null, {}, { 1 }))
        composeRule.onNodeWithTag("summary.leaderboard").assertDoesNotExist()
    }

    @Test
    fun `rank row is absent when the state has no rank`() {
        setContent(HomeLayout.SinglePane, rank = null, summaries = HomeSummaries({}, {}, { 1 }))
        composeRule.onNodeWithTag("summary.leaderboard").assertDoesNotExist()
    }

    @Test
    fun `duels row counts the duels and reports its tap`() {
        var taps = 0
        setContent(
            HomeLayout.SinglePane,
            summaries = HomeSummaries(onOpenRank = null, onOpenDuels = { taps++ }, duelsNeedYou = { 2 }),
        )

        composeRule.onNodeWithTag("summary.duels").performScrollTo().assert(hasText("2 duels need you"))
        composeRule.onNodeWithTag("summary.duels").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `duels row reads singular for one`() {
        setContent(HomeLayout.SinglePane, summaries = HomeSummaries(null, {}, { 1 }))

        composeRule.onNodeWithTag("summary.duels").performScrollTo().assert(hasText("1 duel needs you"))
    }

    @Test
    fun `duels row is absent for zero`() {
        setContent(HomeLayout.SinglePane, summaries = HomeSummaries(null, {}, { 0 }))
        composeRule.onNodeWithTag("summary.duels").assertDoesNotExist()
    }

    @Test
    fun `duels row is absent when no count is offered`() {
        setContent(HomeLayout.SinglePane, summaries = HomeSummaries(null, {}, null))
        composeRule.onNodeWithTag("summary.duels").assertDoesNotExist()
    }

    @Test
    fun `no summaries object means no summary rows`() {
        setContent(HomeLayout.SinglePane, rank = zipRank, summaries = null)

        composeRule.onNodeWithTag("summary.leaderboard").assertDoesNotExist()
        composeRule.onNodeWithTag("summary.duels").assertDoesNotExist()
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 4)

        /** Registry order. A loading badge shimmers forever: the loading test stops the clock. */
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
            stats = GameStats(3, 5, 12, 4, 48_000L, DAY.minusDays(20), best = null, rankOrder = RankOrder.TimeThenHints),
            week = (6L downTo 0L).map { WeekDay(DAY.minusDays(it), emptyList(), isToday = it == 0L) },
            rolloverAt = Instant.now().plus(3, ChronoUnit.HOURS),
        )
    }
}
