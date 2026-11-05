package com.ivandsky.puzzlebox.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.todayStatusLine
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.home.di.homeModule
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.navigation.navigationState
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

/**
 * Route-level regression (controller device walk 2026-11-01; fix round 5, defect C):
 * HomeRoute must observe the GAME-AGNOSTIC merged history — every game's completion
 * records UNION every registered game's captured days. The teaser first showed "No
 * games yet" forever watching an unfed legacy table; after the Zip fix it still dropped
 * every non-Zip record. Only a Zip-id test plugin is registered here, so Queens rows
 * exercise the registry-name fallback. The graph is the production one: domainModule's
 * use cases and homeModule's ViewModel over fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A ref shape Queens authors — local because :feature:home:impl has no games:queens dep. */
    private data class QueensRef(
        override val date: LocalDate,
        override val puzzleNumber: Int?,
    ) : PuzzleRef {
        override val gameId: GameId = GameId("queens")
    }

    private fun queensRecord(day: LocalDate) = CompletionRecord(
        puzzleRef = QueensRef(day, 916),
        time = 41_000L,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    @Before
    fun resetKoin() {
        stopKoin()
    }

    @After
    fun tearDownKoin() {
        stopKoin()
    }

    private val today = LocalDate.of(2026, 11, 4)

    private fun setContent(
        daily: FakeDailyRepository,
        completions: CompletionRepository,
        saved: SavedPlayRepository = FakeSavedPlayRepository(),
        onPlayToday: (GameId) -> Unit = {},
        zipToday: DailyToday = DailyToday.Fresh(TestGames.puzzle(GameId("zip"))),
    ) {
        startGraph(daily, completions, saved, zipToday)
        composeRule.setContent {
            PuzzleBoxTheme {
                HomeRoute(
                    layout = HomeLayout.SinglePane,
                    themeMode = ThemeMode.SYSTEM,
                    onSetThemeMode = {},
                    onPlayToday = onPlayToday,
                    onOpenProfile = {},
                )
            }
        }
    }

    private fun startGraph(
        daily: FakeDailyRepository,
        completions: CompletionRepository,
        saved: SavedPlayRepository = FakeSavedPlayRepository(),
        zipToday: DailyToday = DailyToday.Fresh(TestGames.puzzle(GameId("zip"))),
    ) {
        val fakeIdentity = FakeIdentityService()
        // Home prefetches today's puzzles on resume (A12): by default a fresh fetch keeps Zip not started.
        daily.setToday(GameId("zip"), zipToday)
        startKoin {
            modules(
                module {
                    single<DailyRepository> { daily }
                    single<GameRegistry> { TestGames.registry(listOf(GameId("zip"))) }
                    single<CompletionRepository> { completions }
                    single<IdentityService> { fakeIdentity }
                    single<SavedPlayRepository> { saved }
                    single<GameCalendar> { FakeGameCalendar(today) }
                },
                domainModule,
                homeModule,
            )
        }
    }

    @Test
    fun `play today reports the first unsolved game id`() {
        val played = mutableListOf<GameId>()
        setContent(FakeDailyRepository(), FakeCompletionRepository(), onPlayToday = { played += it })

        composeRule.onNodeWithText("Play today").performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), played) }
    }

    @Test
    fun `showing home prefetches today's puzzle and an unloadable one reads offline - A12`() {
        val daily = FakeDailyRepository()
        setContent(daily, FakeCompletionRepository(), zipToday = DailyToday.Unavailable(FetchError.Unavailable()))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(todayStatusLine(TodayStatus.Offline)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { assertEquals(1, daily.todayCalls(GameId("zip"))) }
        composeRule.onNodeWithTag("home.today_status_zip").assertTextEquals(todayStatusLine(TodayStatus.Offline))
    }

    @Test
    fun `a saved featured play reads resume and still reports the featured game - SC11`() = kotlinx.coroutines.test.runTest {
        val saved = FakeSavedPlayRepository()
        saved.save(PlaySlot(GameId("zip"), today), byteArrayOf(1), elapsedMs = 0L)
        val played = mutableListOf<GameId>()
        setContent(FakeDailyRepository(), FakeCompletionRepository(), saved, onPlayToday = { played += it })

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Resume").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Play today").assertDoesNotExist()
        composeRule.onNodeWithText("Resume").performClick()

        // The same push as "Play today": the play reads its saved row and resumes the board.
        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), played) }
    }

    @Test
    fun `home entry pushes today's play target once on the home stack - N8 N9`() {
        startGraph(FakeDailyRepository(), FakeCompletionRepository())
        val navigator = Navigator(navigationState(HomeKey, PlayKey))
        val provider = entryProvider {
            homeEntry(navigator, HomeLayout.SinglePane, ThemeMode.SYSTEM, onSetThemeMode = {})
        }
        composeRule.setContent {
            PuzzleBoxTheme { provider(HomeKey).Content() }
        }

        // A double tap still lands one game: back from it returns to Home.
        composeRule.onNodeWithText("Play today").performClick()
        composeRule.onNodeWithText("Play today").performClick()

        composeRule.runOnIdle {
            assertEquals(HomeKey, navigator.state.tab)
            assertEquals(listOf(HomeKey, GameKey("zip")), navigator.state.currentStack)
        }
    }

    @Test
    fun `teaser tracks zip daily repository emissions`() {
        val daily = FakeDailyRepository()
        setContent(daily, FakeCompletionRepository())

        // Nothing captured yet: the honest empty state.
        composeRule.onNodeWithText("No games yet").assertExists()

        // A day lands in the compete loop's store (Play fetches/History captures) —
        // the teaser must follow, formatted like the rows the user knows.
        daily.addEntry(DailyEntry(GameId("zip"), LocalDate.of(2026, 10, 31), 592, "zip/n5", 0L))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("October 31").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("October 31").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }

    @Test
    fun `a queens completion surfaces in the teaser - fix round 5 defect C`() = kotlinx.coroutines.test.runTest {
        val completions = FakeCompletionRepository()
        setContent(FakeDailyRepository(), completions)
        composeRule.onNodeWithText("No games yet").assertExists()

        // A solved Queens game writes its record (no Zip capture anywhere) — the teaser
        // must surface it; the row names the game by its id (Queens is not registered in
        // this test's Koin graph, so the registry fallback applies).
        completions.onCompleted(queensRecord(LocalDate.of(2026, 11, 1)))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("November 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("November 1").assertExists()
        composeRule.onNodeWithTag("home.history_row_2026-11-01").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }

    @Test
    fun `today card follows today's set - a zip solve flips the done count`() = kotlinx.coroutines.test.runTest {
        val completions = FakeCompletionRepository()
        setContent(FakeDailyRepository(), completions)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(todayStatusLine(TodayStatus.NotStarted)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.today_summary").assertTextEquals("0 of 1 done")
        composeRule.onNodeWithTag("home.today_status_zip").assertTextEquals(todayStatusLine(TodayStatus.NotStarted))

        // The graph's calendar pins Zip's today: this record is on it.
        completions.onCompleted(
            CompletionRecord(
                puzzleRef = TestGames.Ref(GameId("zip"), today, 592),
                time = 36_000L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
            ),
        )

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1 of 1 done").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.today_status_zip").assertTextEquals(todayStatusLine(TodayStatus.Solved(36_000L)))
    }
}
