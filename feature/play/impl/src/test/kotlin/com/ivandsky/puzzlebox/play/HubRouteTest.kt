package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.todayStatusLine
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
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
 * The Play tab's games hub over stand-in games: one row per playable game in registry
 * order under `route.hub`, opening it prefetches today's puzzles once (A12) and picking a row
 * reports that game without fetching again, and a game reads today's status
 * ([todayStatusLine]) and what a tap does: a save / Continue, a solve / Replay, a failed
 * fetch / Try again, otherwise Play (A13, A14, SC11).
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
                    factory { GetReplaySession(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun setContent(onPick: (GameId) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HubRoute(onPick = onPick)
            }
        }
    }

    @Test
    fun `the hub lists every playable game`() {
        setContent {}

        composeRule.onNodeWithTag("route.hub").assertExists()
        composeRule.onNodeWithTag("hub.row.zip").assertExists()
        composeRule.onNodeWithTag("hub.row.alpha").assertExists()
        composeRule.onNodeWithText("Zip").assertExists()
        composeRule.onNodeWithText("Alpha").assertExists()
    }

    @Test
    fun `opening the hub fetches each game once and picking zip reports zip without fetching again`() {
        val picked = ArrayList<GameId>()
        setContent { picked.add(it) }
        composeRule.waitForIdle()
        assertEquals(1, daily.todayCalls(zip))
        assertEquals(1, daily.todayCalls(alpha))

        composeRule.onNodeWithTag("hub.row.zip").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(GameId("zip")), picked)
        assertEquals(1, daily.todayCalls(zip))
    }

    @Test
    fun `building the hub's view model fetches nothing - only a started hub does`() {
        koin!!.koin.get<HubViewModel>()

        assertEquals(0, daily.todayCalls(zip))
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `a game whose puzzle cannot load reads offline with try again - A12`() {
        daily.setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable()))
        setContent {}

        val offline = todayStatusLine(TodayStatus.Offline)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(offline, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hub.status.alpha", useUnmergedTree = true).assertTextEquals(offline)
        composeRule.onNodeWithTag("hub.action.alpha", useUnmergedTree = true)
            .assertTextEquals(actionLabel(TodayStatus.Offline))
        composeRule.onNodeWithTag("hub.status.zip", useUnmergedTree = true)
            .assertTextEquals(todayStatusLine(TodayStatus.NotStarted))
    }

    @Test
    fun `a game saved today reads in progress with its clock and continue`() {
        runBlocking { saved.save(PlaySlot(zip, today), byteArrayOf(1), elapsedMs = 42_000L) }
        setContent {}

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("hub.status.zip", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hub.status.zip", useUnmergedTree = true).assertTextEquals(todayStatusLine(TodayStatus.InProgress(42_000L)))
        composeRule.onNodeWithTag("hub.action.zip", useUnmergedTree = true).assertTextEquals("Continue")
        composeRule.onNodeWithTag("hub.status.alpha", useUnmergedTree = true)
            .assertTextEquals(todayStatusLine(TodayStatus.NotStarted))
        composeRule.onNodeWithTag("hub.action.alpha", useUnmergedTree = true).assertTextEquals("Play")
    }

    @Test
    fun `a game solved today reads solved with its time and replay`() {
        runBlocking {
            completions.onCompleted(
                CompletionRecord(TestGames.Ref(zip, today, 7), time = 36_000L, hints = 0, stats = RecordStats(1, "{}")),
            )
        }
        setContent {}

        val solved = todayStatusLine(TodayStatus.Solved(36_000L))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(solved, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hub.status.zip", useUnmergedTree = true).assertTextEquals(solved)
        composeRule.onNodeWithTag("hub.action.zip", useUnmergedTree = true).assertTextEquals("Replay")
    }
}
