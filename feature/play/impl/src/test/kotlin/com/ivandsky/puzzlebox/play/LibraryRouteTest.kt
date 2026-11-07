package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.LocalDate
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
 * The Play tab's games library over stand-in games (A25–A29): the tablet shows the list
 * beside the first game's page and a row tap switches it; the phone pushes a page from a
 * row and back returns to the list. The day card's action reports its game; opening the
 * library fetches today once, and an offline row's tap retries. "Open the lesson" runs
 * the game's tutorial. Stats before any solve show the empty layout. The page's calendar
 * reaches any day from #1 to today (‹ › and the jumps) and the day card then plays it. The
 * header's Enter code shows only while friend sharing is on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class LibraryRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val fakes = FakeGames(listOf(zip, alpha))
    private val daily = FakeDailyRepository()
    private val today = LocalDate.parse("2026-11-04")

    /** Every game and day (null = today) a test's day card asked to play, in order. */
    private val played = ArrayList<Pair<GameId, LocalDate?>>()

    private var koin: KoinApplication? = null

    /** Friend sharing on unless a test turns it off before [setContent]. */
    private var flags = FakeReleaseFlags(ReleaseFlag.FriendSharing)

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        daily.setToday(zip, DailyToday.Fresh(TestGames.puzzle(zip)))
        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<ReleaseFlags> { flags }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    factory { ObserveArchiveProgress(get(), get(), get()) }
                    factory { ObserveArchiveMonth(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun setContent(layout: PlayLayout) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryRoute(layout = layout, onPlay = { game, day -> played.add(game to day) }, onOpenCode = {})
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the tablet shows the list beside the first game's page and a row tap switches it`() {
        setContent(PlayLayout.TwoPane)
        awaitTag("library.page.zip")
        composeRule.onNodeWithTag("library.row.zip").assertIsSelected()
        composeRule.onNodeWithTag("library.row.alpha").assertIsNotSelected()
        val list = composeRule.onNodeWithTag("library.list").getUnclippedBoundsInRoot()
        val page = composeRule.onNodeWithTag("library.page.zip").getUnclippedBoundsInRoot()
        assertTrue("the page sits beside the list", page.left >= list.right)
        composeRule.onNodeWithTag("library.page.back").assertDoesNotExist()

        tap("library.row.alpha")
        awaitTag("library.page.alpha")
        composeRule.onNodeWithTag("library.row.alpha").assertIsSelected()
        composeRule.onNodeWithTag("library.page.zip").assertDoesNotExist()
    }

    @Test
    fun `with friend sharing off the header has no Enter code`() {
        flags = FakeReleaseFlags()
        setContent(PlayLayout.SinglePane)
        awaitTag("library.row.zip")
        composeRule.onNodeWithTag("library.enterCode").assertDoesNotExist()
    }

    @Test
    fun `with friend sharing on the header's Enter code opens the code sheet`() {
        setContent(PlayLayout.SinglePane)
        awaitTag("library.enterCode")
        tap("library.enterCode")
        awaitTag("friend.code.sheet")
    }

    @Test
    fun `opening the library fetches today once and the day card reports its game`() {
        setContent(PlayLayout.TwoPane)
        awaitTag("card.status_mark.not_started")
        assertEquals(1, daily.todayCalls(zip))

        tap("library.play")
        assertEquals(listOf(zip to null), played)
        assertEquals(1, daily.todayCalls(zip))
    }

    @Test
    fun `the phone pushes a page from a row and back returns to the list`() {
        setContent(PlayLayout.SinglePane)
        awaitTag("library.row.alpha")
        composeRule.onNodeWithTag("library.page.zip").assertDoesNotExist()

        tap("library.row.alpha")
        awaitTag("library.page.alpha")
        composeRule.onNodeWithTag("library.list").assertDoesNotExist()

        tap("library.page.back")
        awaitTag("library.list")
        composeRule.onNodeWithTag("library.page.alpha").assertDoesNotExist()

        tap("library.row.zip")
        awaitTag("library.page.zip")
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        awaitTag("library.list")
    }

    @Test
    fun `an offline row still opens its page, retries, and the pill shows`() {
        daily.setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable()))
        setContent(PlayLayout.SinglePane)
        awaitTag("card.status_mark.offline")
        awaitTag("library.offline")
        val before = daily.todayCalls(alpha)

        tap("library.row.alpha")
        awaitTag("library.page.alpha")
        composeRule.onNodeWithTag("library.offline").assertExists()
        assertEquals(before + 1, daily.todayCalls(alpha))

        tap("library.play")
        assertEquals("offline, the action retries instead of playing", emptyList<Pair<GameId, LocalDate?>>(), played)
        assertEquals(before + 2, daily.todayCalls(alpha))
    }

    @Test
    fun `open the lesson runs the game's tutorial and back closes it`() {
        fakes[zip].teach(2)
        setContent(PlayLayout.TwoPane)
        awaitTag("library.howto.lesson")

        tap("library.howto.lesson")
        awaitTag("tutorial.host")
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
    }

    @Test
    fun `a game without lessons offers none`() {
        setContent(PlayLayout.TwoPane)
        awaitTag("library.howto")
        composeRule.onNodeWithTag("library.howto.lesson").assertDoesNotExist()
    }

    @Test
    fun `stats before any solve show the empty layout, after a solve the tiles`() {
        val order = RankOrder.TimeThenHints
        fun row(solved: Int) = LibraryRowModel(
            card = GameCardModel(zip, "Zip", GameBrand.Neutral, status = null, rankOrder = order),
            today = null,
            progress = ArchiveProgress(today.minusDays(9)..today, 10, solved, 0),
            stats = if (solved == 0) {
                GameStats(0, 0, 0, 0, null, null, null, order)
            } else {
                GameStats(1, 1, solved, solved, 30_000L, today, BestScore(30_000L, 0), order)
            },
            rules = gameRules(zip),
        )
        val solved = mutableIntStateOf(0)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GameStatsCard(row(solved.intValue))
            }
        }
        composeRule.onNodeWithTag("library.stats.empty").assertExists()

        solved.intValue = 3
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library.stats.empty").assertDoesNotExist()
    }

    @Test
    fun `loading rows draw skeleton marks`() {
        composeRule.mainClock.autoAdvance = false
        val rows = listOf(zip, alpha).map {
            LibraryRowModel(
                card = GameCardModel(it, it.value, GameBrand.Neutral, status = null, rankOrder = RankOrder.TimeThenHints),
                today = null,
                progress = null,
                stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
                rules = emptyList(),
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryScreen(
                    state = LibraryUiState(rows, zip, offline = false),
                    page = null,
                    month = null,
                    layout = PlayLayout.TwoPane,
                    hasLesson = { false },
                    onSelect = {},
                    onPlay = { _, _ -> },
                    onRetry = {},
                    onOpenLesson = {},
                    calendar = CalendarActions(),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        assertEquals(
            3, // two rows plus the day card
            composeRule.onAllNodesWithTag("card.status_mark.loading", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `the calendar reaches any day from #1 to today and the day card plays it`() {
        val first = LocalDate.parse("2025-01-10")
        daily.setArchiveRange(zip, first..today)
        setContent(PlayLayout.TwoPane)
        awaitTag("library.calendar.month.2026-11")
        composeRule.onNodeWithTag("library.calendar.jump.today").assertIsSelected()
        awaitTag("library.day_card.day.$today")

        tap("library.calendar.prev")
        awaitTag("library.calendar.month.2026-10")
        tap("library.calendar.jump.2025")
        awaitTag("library.calendar.month.2025-10")
        composeRule.onNodeWithTag("library.calendar.jump.2025").assertIsSelected()
        tap("library.calendar.jump.first")
        awaitTag("library.calendar.month.2025-01")
        composeRule.onNodeWithTag("library.calendar.prev").assertIsNotEnabled()
        composeRule.onNodeWithTag("library.calendar.day.2025-01-09").assertDoesNotExist()

        tap("library.calendar.day.$first")
        awaitTag("library.day_card.day.$first")
        composeRule.onNodeWithTag("library.day_card.tag.past", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("library.calendar.day.$first").assertIsSelected()
        tap("library.play")
        assertEquals(listOf(zip to first), played)

        tap("library.day_card.back")
        awaitTag("library.day_card.day.$today")
        awaitTag("library.calendar.month.2026-11")
        tap("library.play")
        assertEquals(listOf(zip to first, zip to null), played)
    }

    @Test
    fun `the last day before today plays from today's month`() {
        daily.setArchiveRange(zip, today.minusDays(30)..today)
        setContent(PlayLayout.TwoPane)
        awaitTag("library.calendar.month.2026-11")
        composeRule.onNodeWithTag("library.calendar.next").assertIsNotEnabled()
        val yesterday = today.minusDays(1)
        tap("library.calendar.day.$yesterday")
        awaitTag("library.day_card.day.$yesterday")
        tap("library.play")
        assertEquals(listOf(zip to yesterday), played)
    }
}
