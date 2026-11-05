package com.ivandsky.puzzlebox.play

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
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
 * The Play tab's games hub over stand-in games: one card per playable game in registry
 * order under `route.hub`, opening it prefetches today's puzzles once (A12) and tapping a
 * card (the card itself: there are no buttons) reports that game without fetching again.
 * A game reads today's status ([hubStatusLine]) and shows today's board as its game draws
 * it: a save's board, the cached puzzle fresh or solved, or its glyph when there is none
 * (offline, uncached, unreadable) (A13, A14, SC11). Only the next unsolved game's card is
 * marked up next, a solved card wears the check, the header counts today's solves, a
 * solved game shows its best time, and "Older puzzles" reports through its callback.
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
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), ComputeStreak(GetPuzzleDay(get()))) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun setContent(onOpenHistory: () -> Unit = {}, onPick: (GameId) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HubRoute(layout = PlayLayout.SinglePane, onPick = onPick, onOpenHistory = onOpenHistory)
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

    private fun awaitStatus(game: GameId, status: TodayStatus) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("hub.status.${game.value}") and hasText(hubStatusLine(status)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The fake thumbnail drawn inside [game]'s card thumbnail frame. */
    private fun thumbOf(game: GameId) =
        composeRule.onNode(hasTestTag("fake.thumb") and hasParent(hasTestTag("hub.thumb.${game.value}")), useUnmergedTree = true)

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
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
    fun `opening the hub fetches each game once and tapping zip's card reports zip without fetching again`() {
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
    fun `a game whose puzzle cannot load reads offline with its glyph, and a tap opens it to try again - A12`() {
        daily.setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable()))
        val picked = ArrayList<GameId>()
        setContent { picked.add(it) }

        awaitStatus(alpha, TodayStatus.Offline)
        composeRule.onNodeWithTag("hub.thumb_placeholder.alpha", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub.status.zip", useUnmergedTree = true)
            .assertTextEquals(hubStatusLine(TodayStatus.NotStarted))

        composeRule.onNodeWithTag("hub.row.alpha").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(alpha), picked)
    }

    @Test
    fun `a game saved today reads in progress with its clock and shows the saved board`() {
        val session = FakeController.envelope(zip, moves = 3, day = today)
        runBlocking { saved.save(PlaySlot(zip, today), SessionBlob.encode(zip, session.session), elapsedMs = 42_000L) }
        setContent {}

        awaitStatus(zip, TodayStatus.InProgress(42_000L))
        awaitTag("hub.thumb.zip")
        thumbOf(zip).assertTextEquals("moves 3")
        composeRule.onNodeWithTag("hub.status.alpha", useUnmergedTree = true)
            .assertTextEquals(hubStatusLine(TodayStatus.NotStarted))
    }

    @Test
    fun `a save the game can't read shows the glyph instead of a board`() {
        runBlocking { saved.save(PlaySlot(zip, today), byteArrayOf(1), elapsedMs = 42_000L) }
        setContent {}

        awaitStatus(zip, TodayStatus.InProgress(42_000L))
        composeRule.onNodeWithTag("hub.thumb_placeholder.zip", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub.thumb.zip", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a cached puzzle not started shows today's fresh board - an uncached one its glyph`() {
        daily.setCached(alpha, today, TestGames.puzzle(alpha, tag = 5))
        setContent {}

        awaitTag("hub.thumb.alpha")
        thumbOf(alpha).assertTextEquals("fresh 5")
        composeRule.onNodeWithTag("hub.thumb_placeholder.zip", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a game solved today reads solved with its time, its solved board, the check and its best time`() {
        solveZipToday()
        daily.setCached(zip, today, TestGames.puzzle(zip, tag = 2))
        setContent {}

        awaitStatus(zip, TodayStatus.Solved(36_000L))
        composeRule.onNodeWithTag("hub.solved.zip", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub.solved.alpha", useUnmergedTree = true).assertDoesNotExist()
        awaitTag("hub.thumb.zip")
        thumbOf(zip).assertTextEquals("solved 2")
        awaitTag("hub.best.zip")
        composeRule.onNodeWithTag("hub.best.alpha", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `exactly one card is up next - the next unsolved game's`() {
        solveZipToday()
        setContent {}

        awaitStatus(zip, TodayStatus.Solved(36_000L))
        assertEquals(1, composeRule.onAllNodes(tagStartsWith("hub.next."), useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("hub.next.alpha", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the header counts today's solved games`() {
        solveZipToday()
        setContent {}

        awaitStatus(zip, TodayStatus.Solved(36_000L))
        composeRule.onNodeWithTag("hub.summary").assertTextEquals(hubSummary(done = 1, total = 2))
    }

    @Test
    fun `older puzzles calls back without picking a game`() {
        var opened = 0
        val picked = ArrayList<GameId>()
        setContent(onOpenHistory = { opened++ }) { picked.add(it) }

        composeRule.onNodeWithTag("hub.older").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
        assertEquals(emptyList<GameId>(), picked)
        composeRule.onNodeWithTag("hub.more_coming").assertExists()
    }
}
