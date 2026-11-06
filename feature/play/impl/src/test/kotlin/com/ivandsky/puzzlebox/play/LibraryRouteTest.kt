package com.ivandsky.puzzlebox.play

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Play tab's games hub over stand-in games, in today's sections (A13, A14): the next
 * unsolved game is the one Up-next card, the other unsolved games are tiles under Also
 * today, and solved games are Done-today chips; with no next game the panel takes the
 * hero's place and the side reads Still to play — all solved, the rest offline, or still
 * loading. Opening the hub prefetches today's puzzles once (A12); tapping any card reports
 * that game without fetching again, an offline tile included (its retry). The header counts
 * today's solves and "Older puzzles" reports through its callback. Two-pane puts the hero
 * and the tiles side by side. No state shows a "Past puzzles" button or the old
 * "More ways to play" footer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HubRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val fakes = FakeGames(listOf(zip, alpha))
    private val daily = FakeDailyRepository()
    private val today = LocalDate.parse("2026-11-04")
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()

    /** Every game a test's hub reported picking, in order. */
    private val picked = ArrayList<GameId>()

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        // The hub prefetches today's puzzles when it opens: fresh fetches keep both games playable.
        daily.setToday(zip, DailyToday.Fresh(TestGames.puzzle(zip)))
        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun setContent(
        layout: PlayLayout = PlayLayout.SinglePane,
        onOpenHistory: () -> Unit = {},
        onPick: (GameId) -> Unit,
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HubRoute(layout = layout, onPick = onPick, onOpenHistory = onOpenHistory)
            }
        }
    }

    /** The hub drawn straight from [state], for the states the real sources pass through too fast to catch. */
    private fun setPicker(state: HubUiState, onPick: (GameId) -> Unit = {}) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GamePicker(state = state, layout = PlayLayout.SinglePane, onPick = onPick, onOpenHistory = {})
            }
        }
    }

    private fun solveZipToday() = runBlocking {
        completions.onCompleted(
            CompletionRecord(TestGames.Ref(zip, today, 7), time = 36_000L, hints = 0, stats = RecordStats(1, "{}")),
        )
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Waits for [game]'s card in [card] (`next`, `tile` or `chip`) to show [mark] (a status mark kind). */
    private fun awaitMark(card: String, game: GameId, mark: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(
                hasTestTag("card.status_mark.$mark") and hasAnyAncestor(hasTestTag("card.$card.${game.value}")),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun exists(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()

    private fun absent(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()

    private fun count(prefix: String) = composeRule.onAllNodes(tagStartsWith(prefix), useUnmergedTree = true)

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    /** The per-card "Past puzzles" button and the "more ways to play" footer are gone in every state. */
    private fun assertNoRemovedParts() {
        count("hub.past.").assertCountEquals(0)
        absent("hub.more_coming")
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun `with nothing solved the first game is up next and the rest are tiles under also today`() {
        setContent {}

        awaitMark("next", zip, "not_started")
        exists("route.hub")
        exists("card.tile.alpha")
        count("card.next.").assertCountEquals(1)
        count("card.chip.").assertCountEquals(0)
        absent("card.panel")
        exists("hub.label.also_today")
        absent("hub.label.still_to_play")
        exists("hub.empty.done_today")
        assertNoRemovedParts()
    }

    @Test
    fun `a game solved today is a done chip, the next unsolved game is up next and the header counts it`() {
        solveZipToday()
        setContent {}

        awaitTag("card.chip.zip")
        awaitMark("next", alpha, "not_started")
        absent("card.next.zip")
        absent("card.tile.zip")
        exists("hub.empty.also_today")
        absent("hub.empty.done_today")
        composeRule.onNodeWithTag("hub.summary").assertTextEquals(hubSummary(done = 1, total = 2))
        assertNoRemovedParts()
    }

    @Test
    fun `with every game solved the panel stands in for up next and every game is a chip`() {
        solveZipToday()
        runBlocking {
            completions.onCompleted(
                CompletionRecord(TestGames.Ref(alpha, today, 3), time = 50_000L, hints = 0, stats = RecordStats(1, "{}")),
            )
        }
        setContent {}

        awaitTag("card.chip.alpha")
        exists("card.chip.zip")
        exists("card.panel")
        count("card.next.").assertCountEquals(0)
        count("card.tile.").assertCountEquals(0)
        exists("hub.label.still_to_play")
        exists("hub.empty.also_today")
        composeRule.onNodeWithTag("hub.summary").assertTextEquals(hubSummary(done = 2, total = 2))
        assertNoRemovedParts()
    }

    @Test
    fun `when the rest can't load the panel shows, and tapping the offline tile reports it to try again - A12`() {
        solveZipToday()
        daily.setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable()))
        setContent { picked.add(it) }

        awaitMark("tile", alpha, "offline")
        exists("card.panel")
        exists("card.chip.zip")
        exists("hub.label.still_to_play")
        assertNoRemovedParts()

        composeRule.onNodeWithTag("card.tile.alpha").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(alpha), picked)
    }

    @Test
    fun `while statuses are unknown the panel shows loading and every game is a tile under still to play`() {
        // The skeletons' sheen never settles: drive the clock by hand.
        composeRule.mainClock.autoAdvance = false
        val rows = listOf(zip, alpha).map { HubRow(it, it.value, RankOrder.TimeThenHints) }
        setPicker(HubUiState(rows))
        composeRule.mainClock.advanceTimeBy(1_000L)

        exists("card.panel")
        exists("card.tile.zip")
        exists("card.tile.alpha")
        count("card.status_mark.loading").assertCountEquals(2)
        count("card.next.").assertCountEquals(0)
        exists("hub.label.still_to_play")
        exists("hub.empty.done_today")
        assertNoRemovedParts()
    }

    @Test
    fun `opening the hub fetches each game once and tapping zip's card reports zip without fetching again`() {
        setContent { picked.add(it) }
        awaitTag("card.next.zip")
        assertEquals(1, daily.todayCalls(zip))
        assertEquals(1, daily.todayCalls(alpha))

        composeRule.onNodeWithTag("card.next.zip").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(zip), picked)
        assertEquals(1, daily.todayCalls(zip))
    }

    @Test
    fun `tapping a tile or a chip reports its game`() {
        setPicker(
            HubUiState(
                rows = listOf(
                    HubRow(zip, "Zip", RankOrder.TimeThenHints, status = TodayStatus.Solved(36_000L)),
                    HubRow(alpha, "Alpha", RankOrder.TimeThenHints, status = TodayStatus.NotStarted),
                    HubRow(TestGames.beta, "Beta", RankOrder.TimeThenHints, status = TodayStatus.NotStarted),
                ),
                doneCount = 1,
                next = alpha,
            ),
        ) { picked.add(it) }

        composeRule.onNodeWithTag("card.tile.beta").performScrollTo().performClick()
        composeRule.onNodeWithTag("card.chip.zip").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(TestGames.beta, zip), picked)
    }

    @Test
    fun `building the hub's view model fetches nothing - only a started hub does`() {
        koin!!.koin.get<HubViewModel>()

        assertEquals(0, daily.todayCalls(zip))
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `older puzzles calls back without picking a game`() {
        var opened = 0
        setContent(onOpenHistory = { opened++ }) { picked.add(it) }
        awaitTag("card.next.zip")

        composeRule.onNodeWithTag("hub.older").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
        assertEquals(emptyList<GameId>(), picked)
    }

    @Test
    fun `a phone stacks up next above the tiles`() {
        setContent {}
        awaitTag("card.next.zip")

        assertTrue(bounds("card.tile.alpha").top >= bounds("card.next.zip").bottom)
    }

    @Test
    fun `two-pane puts up next and the tiles side by side, with done today below both`() {
        setContent(layout = PlayLayout.TwoPane) {}
        awaitTag("card.next.zip")

        val hero = bounds("card.next.zip")
        val tile = bounds("card.tile.alpha")
        assertTrue(tile.left >= hero.right)
        assertTrue(tile.top > hero.top && tile.top < hero.bottom)
        assertTrue(bounds("hub.empty.done_today").top >= hero.bottom)
    }
}
