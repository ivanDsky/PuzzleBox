package com.ivandsky.puzzlebox.app.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Over the real Zip game: the result route reads its slot's live play — solving stamps
 * the badge in memory (fire-and-observe, never waited on) — and "Play again" hands back
 * to navigation, whose reopen of the same day resets the SAME puzzle to a fresh session.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = android.app.Application::class)
class RealGamesResultRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `result route reads its slot's live play and play again reopens a fresh attempt`() {
        val today = ZipDefinition.cadence.today()
        val daily = freshDailyForAllGames().apply {
            setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(today)))
        }
        val koin = startGraph(daily, today)
        val store = koin.koin.get<PlaySessionStore>()
        val gameKey = GameKey(ZipDefinition.id.value, today.toString())
        fun zip() = store.peek(PlaySlot.Daily(ZipDefinition.id, today))!!.controller as ZipPlayController
        try {
            // Bring the slot to a solved play the way a play-through would.
            composeRule.runOnIdle { store.open(gameKey) }
            composeRule.waitUntil { zip().session != null }
            composeRule.runOnIdle {
                ZipPlayFixture.solution.forEach { cell ->
                    zip().onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
                }
            }
            composeRule.waitForIdle()
            // The route speaks for the live play only for the solve it holds (F2): key it by that solve.
            val solved = composeRule.runOnIdle { checkNotNull(zip().session) }
            var replayed = false
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(ZipDefinition.id.value, today.toString(), solved.elapsedMs, solved.hintsUsed),
                        onPlayAgain = { replayed = true },
                        onNext = {},
                        onDone = {},
                    )
                }
            }
            // The verdict is in memory: the game's first solve (no prior records) stamps
            // the pill; the key's facts show.
            composeRule.onNodeWithTag("result.hero.screen").assertExists()
            composeRule.onNodeWithTag("result.hero.time").assertExists()
            composeRule.onNodeWithTag("result.hero.pill").assertExists()

            // "Play again": navigation is handed back; the explicit replay is a fresh session.
            composeRule.onNodeWithTag("result.playAgain").performScrollTo().performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                store.replay(ResultKey(ZipDefinition.id.value, today.toString(), solved.elapsedMs, solved.hintsUsed))
                assertTrue(zip().session?.path?.isEmpty() == true)
                assertTrue(zip().session?.isComplete == false)
            }
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }

    @Test
    fun `the daily board shows on today's result`() {
        assertBoard(shown = true)
    }


    @Test
    fun `signed out on the hero page the rank row is the one sign-in action`() {
        val today = ZipDefinition.cadence.today()
        val daily = freshDailyForAllGames().apply {
            setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(today)))
        }
        startGraph(daily, today)
        try {
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(ZipDefinition.id.value, today.toString(), 95_000L, 0),
                        onPlayAgain = {},
                        onNext = {},
                        onDone = {},
                    )
                }
            }
            composeRule.waitForIdle()
            // The board shows (signed out) and says text only; the rank row keeps the action (RG11).
            composeRule.onNodeWithTag("daily_board.title").assertExists()
            composeRule.onNodeWithText("Sign in to see today's board").assertExists()
            composeRule.onNodeWithTag("result.side.rank.signin").assertExists()
            composeRule.onNodeWithTag("custom_board.sign_in").assertDoesNotExist()
        } finally {
            stopKoin()
        }
    }

    /** Today's numbered Zip result: the board shows. */
    private fun assertBoard(shown: Boolean) {
        val today = ZipDefinition.cadence.today()
        val daily = freshDailyForAllGames().apply {
            setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(today)))
        }
        startGraph(daily, today)
        try {
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(ZipDefinition.id.value, today.toString(), 95_000L, 0),
                        onPlayAgain = {},
                        onNext = {},
                        onDone = {},
                    )
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("result.hero.screen").assertExists()
            val board = composeRule.onNodeWithTag("daily_board.title")
            if (shown) board.assertExists() else board.assertDoesNotExist()
        } finally {
            stopKoin()
        }
    }

    private object DismissedGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken =
            GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
    }

    private fun startGraph(daily: FakeDailyRepository, today: LocalDate): KoinApplication {
        stopKoin() // tests share the JVM-global Koin context
        return startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single { GameRegistry(listOf(zipPlugin)) }
                    single { GamePlayRegistry(listOf(ZipPlayUi())) }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { GetPuzzleDay(get()) }
                    factory { ComputeStreak(get()) }
                    factory { ObserveGameStats(get(), get(), get()) }
                    factory { GetNextRollover(get()) }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                    single { SubmitStatusTracker() }
                    single<CustomBoardRepository> { FakeCustomBoardRepository() }
                    single<GoogleIdTokenProvider> { DismissedGoogleIdTokenProvider }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                    factory { ObserveResultRank(get()) }
                    factory { ObserveResultInsights(get(), get(), get(), get()) }
                    // Tutorial seen: this path is about the result; the lessons have their own tests.
                    single<UserPreferencesRepository> {
                        FakeUserPreferencesRepository(seen = setOf(ZipDefinition.id))
                    }
                },
                playModule,
            )
        }
    }
}
