package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.update.AppRelease
import com.ivandsky.puzzlebox.data.update.AppReleaseRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.home.di.homeModule
import com.ivandsky.puzzlebox.home.navigation.homeEntry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.navigation.navigationState
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeAppReleaseRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Route-level regression (controller device walk 2026-11-01; fix round 5, defect C):
 * HomeRoute must observe the GAME-AGNOSTIC merged history — every game's completion
 * records UNION every registered game's captured days. The teaser first showed "No
 * games yet" forever watching an unfed legacy table; after the Zip fix it still dropped
 * every non-Zip record. Only a Zip-id test plugin is registered here; records of a game
 * the registry does not list never surface (ADR-0015). The graph is the production one: domainModule's
 * use cases and homeModule's ViewModel over fakes (no game UI registered: neutral
 * brands). Copy is never pinned: tags, enabled state and callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        onOpenGame: (GameId) -> Unit = {},
        zipToday: DailyToday = DailyToday.Fresh(TestGames.puzzle(GameId("zip"))),
    ) {
        startGraph(daily, completions, saved, zipToday)
        composeRule.setContent {
            PuzzleBoxTheme {
                HomeRoute(
                    layout = HomeLayout.SinglePane,
                    onOpenGame = onOpenGame,
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
        releases: AppReleaseRepository = FakeAppReleaseRepository(),
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
                    single<GamePlayRegistry> { GamePlayRegistry(emptyList()) }
                    // Resume also checks for an update: by default no release known, so no pill.
                    single<AppReleaseRepository> { releases }
                    single { AppBuild(versionName = "1.0.0", checksForUpdates = true) }
                },
                domainModule,
                homeModule,
            )
        }
    }

    @Test
    fun `the CTA names and reports the first unsolved game`() {
        val played = mutableListOf<GameId>()
        setContent(FakeDailyRepository(), FakeCompletionRepository(), onOpenGame = { played += it })

        composeRule.onNodeWithTag("home.today_play")
            .assert(hasText("Zip", substring = true))
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), played) }
    }

    @Test
    fun `showing home prefetches today's puzzle and an unloadable one's badge stays a retry - A9 A12`() {
        val daily = FakeDailyRepository()
        val played = mutableListOf<GameId>()
        setContent(
            daily,
            FakeCompletionRepository(),
            onOpenGame = { played += it },
            zipToday = DailyToday.Unavailable(FetchError.Unavailable()),
        )

        composeRule.waitUntil(5_000) { daily.todayCalls(GameId("zip")) == 1 }
        composeRule.onNodeWithTag("home.badge.zip").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), played) }
    }

    @Test
    fun `a saved play keeps the CTA on that game - SC11`() = kotlinx.coroutines.test.runTest {
        val saved = FakeSavedPlayRepository()
        saved.save(PlaySlot.Daily(GameId("zip"), today), byteArrayOf(1), elapsedMs = 0L)
        val played = mutableListOf<GameId>()
        setContent(FakeDailyRepository(), FakeCompletionRepository(), saved, onOpenGame = { played += it })

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home.today_play").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.today_play")
            .assert(hasText("Zip", substring = true))
            .performClick()

        // The same push as a fresh play: the play reads its saved row and resumes the board.
        composeRule.runOnIdle { assertEquals(listOf(GameId("zip")), played) }
    }

    @Test
    fun `home entry pushes today's play target once on the home stack - N8 N9`() {
        startGraph(FakeDailyRepository(), FakeCompletionRepository())
        val navigator = Navigator(navigationState(HomeKey, PlayKey))
        val provider = entryProvider {
            homeEntry(navigator, HomeLayout.SinglePane)
        }
        composeRule.setContent {
            PuzzleBoxTheme { provider(HomeKey).Content() }
        }

        // A double tap still lands one game: back from it returns to Home.
        composeRule.onNodeWithTag("home.today_play").performClick()
        composeRule.onNodeWithTag("home.today_play").performClick()

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
    fun `a completion without a captured day surfaces in the teaser - fix round 5 defect C`() = kotlinx.coroutines.test.runTest {
        val completions = FakeCompletionRepository()
        setContent(FakeDailyRepository(), completions)
        composeRule.onNodeWithText("No games yet").assertExists()

        // A solved game writes only its record (no daily capture anywhere) — the teaser
        // must still surface it.
        completions.onCompleted(
            CompletionRecord(
                puzzleRef = TestGames.Ref(GameId("zip"), PuzzleKey.Daily(LocalDate.of(2026, 11, 1), 916)),
                time = 41_000L,
                hints = 1,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
            ),
        )

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("November 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("November 1").assertExists()
        composeRule.onNodeWithTag("home.history_row_2026-11-01").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }

    @Test
    fun `a zip solve today turns the CTA into the countdown`() = kotlinx.coroutines.test.runTest {
        val completions = FakeCompletionRepository()
        setContent(FakeDailyRepository(), completions)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home.today_play").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.today_countdown").assertDoesNotExist()

        // The graph's calendar pins Zip's today: this record is on it, and Zip is the whole set.
        completions.onCompleted(
            CompletionRecord(
                puzzleRef = TestGames.Ref(GameId("zip"), PuzzleKey.Daily(today, 592)),
                time = 36_000L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
            ),
        )

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home.today_countdown").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.today_play").assertDoesNotExist()
    }

    @Test
    fun `a newer release shows the pill and its tap opens the release page - S4`() {
        val url = "https://github.com/ivanDsky/PuzzleBox/releases/tag/v1.3.0"
        startGraph(
            FakeDailyRepository(),
            FakeCompletionRepository(),
            releases = FakeAppReleaseRepository(AppRelease(tag = "v1.3.0", url = url)),
        )
        val opened = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                PuzzleBoxTheme {
                    HomeRoute(layout = HomeLayout.SinglePane, onOpenGame = {}, onOpenProfile = {})
                }
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home.update_pill").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.update_pill").performClick()

        composeRule.runOnIdle { assertEquals(listOf(url), opened) }
    }

    @Test
    fun `a tap with no app to open the link does nothing - A6`() {
        val url = "https://github.com/ivanDsky/PuzzleBox/releases/tag/v1.3.0"
        startGraph(
            FakeDailyRepository(),
            FakeCompletionRepository(),
            releases = FakeAppReleaseRepository(AppRelease(tag = "v1.3.0", url = url)),
        )
        // What AndroidUriHandler throws when no activity can handle the link.
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) = throw IllegalArgumentException("Can't open $uri.")
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                PuzzleBoxTheme {
                    HomeRoute(layout = HomeLayout.SinglePane, onOpenGame = {}, onOpenProfile = {})
                }
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("home.update_pill").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home.update_pill").performClick()

        composeRule.onNodeWithTag("home.update_pill").assertExists()
    }
}
