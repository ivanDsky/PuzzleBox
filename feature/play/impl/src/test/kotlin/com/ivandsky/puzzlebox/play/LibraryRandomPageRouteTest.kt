package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.CommunityFixture
import com.ivandsky.puzzlebox.play.community.HOUR
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The library route's Random, which lives only in a game page's calendar: the chip opens the sheet with that game's chip
 * chosen (Past puzzles, Not solved yet on) and the pick plays that game's past day, or opens a New today puzzle's code.
 * One chip is pinned per test so no seed picks the game. A game with no archive has no chip on its page, and the Games tab header has no Random.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryRandomPageRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val bare = GameId("bare")
    private val today = LocalDate.parse("2026-11-04")
    private val played = mutableListOf<Pair<GameId, LocalDate?>>()
    private val opened = mutableListOf<ShareCode>()
    private var koin: KoinApplication? = null

    private fun start() {
        stopKoin() // tests share the JVM-global Koin context
        val fakes = FakeGames(listOf(zip, alpha, bare))
        val daily = FakeDailyRepository()
        listOf(zip, alpha, bare).forEach { daily.setToday(it, TestGames.puzzle(it)) }
        // bare has no archive range, so its page has nothing to pick from.
        listOf(zip, alpha).forEach {
            daily.setArchiveRange(it, today.minusDays(5)..today)
            // An in-range day nobody scripted would fetch, so each past day is scripted.
            (1L..5L).forEach { back -> daily.setDay(it, today.minusDays(back), DailyDay.Ready(TestGames.puzzle(it), 100)) }
        }
        val community = CommunityFixture().also { runBlocking { it.publish("AAA111", NOW - HOUR, ALPHA) } }
        val identity = FakeIdentityService(Identity("me", "Me", null))
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<HiddenPuzzles> { community.hidden }
                    single<SharedPuzzleRepository> { community.shared }
                    single<MatchRepository> { FakeMatchRepository() }
                    single<IdentityService> { identity }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    // A game page's rank row builds this; nobody has ranked.
                    single<LeaderboardRepository> { FakeLeaderboardRepository(emptyMap()) }
                    single<GoogleIdTokenProvider> { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                    single {
                        val random = Random(1)
                        val registry = get<GameRegistry>()
                        LoadRandomPuzzle(
                            LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, random),
                            LoadRandomShared(ListCommunityPuzzles(community.shared, community.hidden, identity) { NOW }, random),
                        )
                    }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                // After playModule, whose own FinishLeg would replace this one.
                module { factory { FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), identity) { Instant.EPOCH } } },
                hubOffFriendDay,
                module { single<DispatcherProvider> { testDispatchers() } },
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun mount() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryRoute(
                    layout = PlayLayout.SinglePane,
                    onPlay = { g, day -> played += g to day },
                    onOpenCode = { opened += it },
                    onOpenRanks = { _, _ -> },
                    onMakeOne = {},
                )
            }
        }
    }

    private fun openPage(game: GameId) {
        mount()
        awaitTag("library.row.${game.value}")
        tap("library.row.${game.value}")
        awaitTag("library.page.${game.value}")
    }

    private fun count(tag: String): Int = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun awaitTag(tag: String) = composeRule.waitUntil(TIMEOUT_MS) { count(tag) > 0 }

    private fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()

    private fun pageRandom() = composeRule.onNode(hasTestTag("library.page.random") and hasAnyAncestor(hasTestTag("library.calendar")))

    private fun pickFromPage(game: GameId) {
        start()
        openPage(game)

        pageRandom().performScrollTo().performClick()
        awaitTag("random.sheet")
        composeRule.onNodeWithTag("random.game.${game.value}").assertIsSelected()
        composeRule.onNodeWithTag("random.unsolved").assertIsOn()
        tap("random.pick")
        composeRule.waitUntil(TIMEOUT_MS) { played.isNotEmpty() }

        assertEquals(game, played.single().first)
        assertTrue("${played.single().second} is before $today", played.single().second!! < today)
    }

    @Test
    fun `Random on the zip page presets zip and plays a past zip day`() = pickFromPage(zip)

    @Test
    fun `Random on the alpha page presets alpha and plays a past alpha day`() = pickFromPage(alpha)

    @Test
    fun `a New today pick from the alpha page opens that puzzle's code and plays nothing`() {
        start()
        openPage(alpha)

        pageRandom().performScrollTo().performClick()
        awaitTag("random.sheet")
        tap("random.source.community")
        tap("random.pick")
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }

        assertEquals("AAA111", opened.single().value)
        assertTrue(played.isEmpty())
    }

    @Test
    fun `the Games tab header has no Random`() {
        start()
        mount()
        awaitTag("library.enterCode")

        assertEquals(0, count("library.random"))
        assertEquals(0, count("random.sheet"))
    }

    @Test
    fun `a game with no archive has no Random on its page`() {
        start()
        openPage(bare)

        assertEquals(0, count("library.page.random"))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
