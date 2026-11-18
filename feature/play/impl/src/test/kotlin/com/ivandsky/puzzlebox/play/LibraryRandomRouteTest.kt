package com.ivandsky.puzzlebox.play

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
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
import com.ivandsky.puzzlebox.play.random.RandomPuzzleEntry
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

/**
 * The Games tab's Random button over the real route, sheet and random use cases: zip and alpha each have five past days,
 * and New today lists exactly one puzzle (AAA111), so a New today pick is never down to the seed. The holder is bound
 * per test; with it off the route has neither the button nor the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LibraryRandomRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val played = mutableListOf<Pair<GameId, LocalDate?>>()
    private val opened = mutableListOf<ShareCode>()
    private var koin: KoinApplication? = null

    private fun start(holder: RandomPuzzleEntry?) {
        stopKoin() // tests share the JVM-global Koin context
        val fakes = FakeGames(listOf(zip, alpha))
        val daily = FakeDailyRepository()
        listOf(zip, alpha).forEach {
            daily.setToday(it, TestGames.puzzle(it))
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
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), identity) { Instant.EPOCH } }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    factory { ObserveArchiveProgress(get(), get(), get()) }
                    factory { ObserveArchiveMonth(get(), get(), get(), get()) }
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
                hubOffFriendDay,
                module { holder?.let { entry -> single { entry } } },
                module { single<DispatcherProvider> { testDispatchers() } },
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private val slot = DuelsSlot(
        content = { _, _, _ -> Text("duels body", Modifier.testTag("duels.body")) },
        needYou = { 0 },
        onOpenDuel = {},
        pane = { _ -> },
    )

    private fun mount(duels: DuelsSlot? = null) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryRoute(
                    layout = PlayLayout.SinglePane,
                    onPlay = { game, day -> played += game to day },
                    onOpenCode = { opened += it },
                    onOpenRanks = { _, _ -> },
                    duels = duels,
                    onMakeOne = {},
                )
            }
        }
        awaitTag("library.row.zip")
    }

    private fun count(tag: String): Int = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun awaitTag(tag: String) = composeRule.waitUntil(TIMEOUT_MS) { count(tag) > 0 }

    private fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()

    private fun pickPast(game: GameId) {
        mount()
        tap("library.random")
        awaitTag("random.sheet")
        tap("random.game.${game.value}")
        tap("random.pick")
        composeRule.waitUntil(TIMEOUT_MS) { played.isNotEmpty() }
        assertEquals(game, played.single().first)
        assertTrue("${played.single().second} is before $today", played.single().second!! < today)
    }

    @Test
    fun `Random opens the sheet and a zip pick plays a past zip day`() {
        start(RandomPuzzleEntry(true))
        pickPast(zip)
    }

    @Test
    fun `Random opens the sheet and an alpha pick plays a past alpha day`() {
        start(RandomPuzzleEntry(true))
        pickPast(alpha)
    }

    @Test
    fun `a New today pick opens that puzzle's code and plays nothing`() {
        start(RandomPuzzleEntry(true))
        mount()
        tap("library.random")
        awaitTag("random.sheet")
        tap("random.source.community")
        tap("random.pick")
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        assertEquals("AAA111", opened.single().value)
        assertTrue(played.isEmpty())
    }

    @Test
    fun `with the holder off there is no Random button and no sheet`() {
        start(RandomPuzzleEntry(false))
        mount()
        assertEquals(0, count("library.random"))
        assertEquals(0, count("random.sheet"))
        assertEquals(1, count("library.enterCode"))
    }

    @Test
    fun `the playModule default holder is off`() {
        start(holder = null)
        mount()
        assertEquals(0, count("library.random"))
    }

    @Test
    fun `Random is on the Dailies tab only`() {
        start(RandomPuzzleEntry(true))
        mount(slot)
        assertEquals(1, count("library.random"))

        tap("library.tab.duels")
        awaitTag("duels.body")
        assertEquals(0, count("library.random"))

        tap("library.tab.dailies")
        awaitTag("library.random")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
