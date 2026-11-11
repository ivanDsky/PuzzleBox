package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * The tablet's games list on a short window: with From friends above many games, the list
 * scrolls, so the section and every game row stay reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h560dp")
class LibraryTabletScrollTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ids = List(8) { GameId("game$it") }
    private val zip = ids.first()
    private val fakes = FakeGames(ids)
    private val daily = FakeDailyRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val today = LocalDate.parse("2026-11-04")

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        ids.forEach { daily.setToday(it, DailyToday.Fresh(TestGames.puzzle(it))) }
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().main) }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<ReceivedPuzzleRepository> { received }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    factory { ObserveArchiveProgress(get(), get(), get()) }
                    factory { ObserveArchiveMonth(get(), get(), get(), get()) }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository(emptyMap()) }
                    single<GoogleIdTokenProvider> { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    @Test
    fun `the tablet list scrolls to its last game below From friends`() {
        runBlocking {
            received.upsert(puzzle(A))
            received.upsert(puzzle(B, openedAt = 1))
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                LibraryRoute(layout = PlayLayout.TwoPane, onPlay = { _, _ -> }, onOpenCode = {}, onOpenGameHistory = { _, _ -> })
            }
        }
        awaitTag("library.friends.row.${A.value}")
        awaitTag("library.row.${ids.last().value}")

        composeRule.onNodeWithTag("library.row.${ids.last().value}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("library.friends").performScrollTo().assertIsDisplayed()
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun puzzle(code: ShareCode, openedAt: Long = 0, game: GameId = zip) = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = TestGames.variant(game),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000 + openedAt),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private companion object {
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
        val B = checkNotNull(ShareCode.parse("B4R9TZ"))
    }
}
