package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
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
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
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
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
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
 * The win-moment coverage. [ResultScreen] (a friend puzzle's result): the header, the time
 * and the Hints tile; the pill rides the in-memory verdict, absent while the write is in
 * flight; a replay names the best it left standing; Done, Share and "Play again" hand back.
 * Then the daily [ResultRoute] on the hero page: its slot's live play, the key's facts alone,
 * and Next for the next unsolved game. Assertions ride tags, never copy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val day = TestGames.definition(game).cadence.today()

    private fun setContent(
        verdict: RecordVerdict? = null,
        previousBestMs: Long? = null,
        previousHints: Int = 0,
        rankOrder: RankOrder = RankOrder.TimeThenHints,
        onDone: () -> Unit = {},
        onShare: () -> Unit = {},
        onPlayAgain: (() -> Unit)? = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultScreen(
                    timeMs = 95_000,
                    hintsUsed = 1,
                    onDone = onDone,
                    onShare = onShare,
                    onPlayAgain = onPlayAgain,
                    game = "Alpha",
                    verdict = verdict,
                    previousBest = previousBestMs?.let { BestScore(it, previousHints) },
                    rankOrder = rankOrder,
                )
            }
        }
    }

    @Test
    fun `shows the header, the time and the hints tile`() {
        setContent()
        listOf("result.screen", "result.stamp", "result.label", "result.time", "result.tile.hints")
            .forEach { composeRule.onNodeWithTag(it).assertExists() }
        composeRule.onNodeWithTag("result.tile.streak").assertDoesNotExist()
        composeRule.onNodeWithTag("result.tile.today").assertDoesNotExist()
    }

    @Test
    fun `a record shows the pill and no replay line`() {
        setContent(verdict = RecordVerdict.NewBest)
        composeRule.onNodeWithTag("result.record_badge").assertExists()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
    }

    @Test
    fun `record pill absent while the write is still in flight`() {
        setContent(verdict = null, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
    }

    @Test
    fun `a replay claims no pill and says it was a replay`() {
        setContent(verdict = RecordVerdict.Replay, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.previous_best").assertExists()
    }

    @Test
    fun `a hints-first replay leads its best line with the hints`() {
        setContent(verdict = RecordVerdict.Replay, previousBestMs = 102_000, rankOrder = RankOrder.HintsThenTime)
        composeRule.onNodeWithText("Replay · Best Perfect · 1:42").assertExists()
    }

    @Test
    fun `a time-first best line never shows hints`() {
        setContent(verdict = RecordVerdict.Replay, previousBestMs = 80_000, previousHints = 2)
        composeRule.onNodeWithText("Replay · Best 1:20").assertExists()
    }

    @Test
    fun `play again hands back to navigation`() {
        var replayed = 0
        setContent(onPlayAgain = { replayed++ })
        composeRule.onNodeWithTag("result.playAgain").performClick()
        composeRule.runOnIdle { assertEquals(1, replayed) }
    }

    @Test
    fun `share hands back to the route`() {
        var shared = 0
        setContent(onShare = { shared++ })
        composeRule.onNodeWithTag("result.share").performClick()
        composeRule.runOnIdle { assertEquals(1, shared) }
    }

    @Test
    fun `done hands back and is the only primary step`() {
        var done = 0
        setContent(onDone = { done++ })
        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
        composeRule.onNodeWithTag("result.all_done").assertDoesNotExist()
        composeRule.onNodeWithTag("result.done").performClick()
        composeRule.runOnIdle { assertEquals(1, done) }
    }

    @Test
    fun `no play again when none is handed in`() {
        setContent(onPlayAgain = null)
        composeRule.onNodeWithTag("result.playAgain").assertDoesNotExist()
        composeRule.onNodeWithTag("result.share").assertExists()
    }

    /**
     * The route wiring end-to-end on the store: the slot's solved live play stamps the
     * pill (the write is fire-and-observe — the surface never waits for it), Up hands back
     * to navigation, the only game solved shows the countdown, and "Play again" hands back
     * so the store's explicit replay resets the SAME puzzle to a fresh session.
     */
    @Test
    fun `result route reads its slot's live play`() {
        val fakes = FakeGames(listOf(game))
        val daily = FakeDailyRepository().apply {
            setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
            setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
        }
        val koin = startGraph(fakes, daily)
        val store = koin.koin.get<PlaySessionStore>()
        val gameKey = GameKey(game.value, day.toString())
        try {
            composeRule.runOnIdle { store.open(gameKey) }
            composeRule.waitForIdle()
            composeRule.runOnIdle { fakes[game].latest.win() }
            composeRule.waitForIdle()
            var replayed = false
            var done = 0
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(game.value, day.toString(), FakeController.TIME_MS, 0),
                        onPlayAgain = { replayed = true },
                        onNext = {},
                        onDone = { done++ },
                        onOpenBoard = {},
                    )
                }
            }
            composeRule.onNodeWithTag("result.hero.screen").assertExists()
            // The verdict is in memory: the game's first solve pill renders.
            composeRule.onNodeWithTag("result.hero.pill").assertExists()
            composeRule.onNodeWithTag("result.hero.title").assertExists()
            composeRule.onNodeWithTag("result.hero.chip.today").assertExists()

            composeRule.onNodeWithTag("result.up").performClick()
            composeRule.runOnIdle { assertEquals(1, done) }

            // The only game is solved: the countdown stands where Next would.
            composeRule.onNodeWithTag("result.all_done").assertExists()
            composeRule.onNodeWithTag("result.countdown").assertExists()
            composeRule.onNodeWithTag("result.next").assertDoesNotExist()

            // The hero page scrolls: bring the button on screen before the tap.
            composeRule.onNodeWithTag("result.playAgain").performScrollTo().performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                store.playAgain(ResultKey(game.value, day.toString(), FakeController.TIME_MS, 0))
                val controller = fakes[game].latest
                assertEquals(1, controller.replays)
                assertTrue(!controller.isComplete)
            }
        } finally {
            koin.close()
        }
    }

    @Test
    fun `result route without a live play renders the key's facts and no pill`() {
        val koin = startGraph(FakeGames(listOf(game)), FakeDailyRepository())
        try {
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(game.value, day.toString(), 12_340, 0),
                        onPlayAgain = {},
                        onNext = {},
                        onDone = {},
                        onOpenBoard = {},
                    )
                }
            }
            composeRule.onNodeWithTag("result.hero.time").assertExists()
            composeRule.onNodeWithTag("result.hero.pill").assertDoesNotExist()
            composeRule.onNodeWithTag("result.write_failed").assertDoesNotExist()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `result route draws the hero page and hands back Up and Next`() {
        val koin = startGraph(FakeGames(listOf(game, TestGames.beta)), FakeDailyRepository())
        try {
            var next: GameId? = null
            var done = 0
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(game.value, day.toString(), 12_340, 0),
                        onPlayAgain = {},
                        onNext = { next = it },
                        onDone = { done++ },
                        onOpenBoard = {},
                    )
                }
            }
            composeRule.onNodeWithTag("result.hero.screen").assertExists()
            composeRule.onNodeWithTag("result.hero.time").assertExists()

            composeRule.onNodeWithTag("result.up").performClick()
            composeRule.runOnIdle { assertEquals(1, done) }
            composeRule.onNodeWithTag("result.next").performClick()
            composeRule.runOnIdle { assertEquals(TestGames.beta, next) }
        } finally {
            koin.close()
        }
    }

    @Test
    fun `the hero page's date line leads today's result with Today`() {
        val saturday = LocalDate.of(2026, 11, 7)
        assertEquals("Today · Sat 7 Nov", resultDateLine(saturday, today = true, locale = Locale.US))
        assertEquals("Sat 7 Nov", resultDateLine(saturday, today = false, locale = Locale.US))
    }

    @Test
    fun `today's result route hands next the next unsolved game`() {
        val koin = startGraph(FakeGames(listOf(game, TestGames.beta)), FakeDailyRepository())
        try {
            var next: GameId? = null
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(game.value, day.toString(), 12_340, 0),
                        onPlayAgain = {},
                        onNext = { next = it },
                        onDone = {},
                        onOpenBoard = {},
                    )
                }
            }
            composeRule.onNodeWithTag("result.next").performClick()
            composeRule.runOnIdle { assertEquals(TestGames.beta, next) }
        } finally {
            koin.close()
        }
    }

    @Test
    fun `today's official result draws one row that reports the game`() {
        var opened = mutableListOf<GameId>()
        withResult(day, onOpenBoard = { opened += it }) {
            composeRule.onNodeWithTag("summary.leaderboard").assertExists()
            composeRule.onNodeWithTag("daily_board.title").assertDoesNotExist()
            composeRule.onNodeWithTag("summary.leaderboard").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(listOf(game), opened) }
        }
    }

    @Test
    fun `a past day's replay draws neither the row nor the board`() {
        withResult(day.minusDays(1), onOpenBoard = {}) {
            composeRule.onNodeWithTag("summary.leaderboard").assertDoesNotExist()
            composeRule.onNodeWithTag("daily_board.title").assertDoesNotExist()
        }
    }

    @Test
    fun `signed out, the row draws no sign-in of its own`() {
        withResult(day, onOpenBoard = {}) {
            composeRule.onNodeWithTag("summary.leaderboard").assertExists()
            composeRule.onNodeWithTag("custom_board.sign_in").assertDoesNotExist()
        }
    }

    /** Renders [ResultRoute] for [resultDay] on an official puzzle (history seeded for today and yesterday), then runs [check]. */
    private fun withResult(resultDay: LocalDate, onOpenBoard: (GameId) -> Unit, check: () -> Unit) {
        val daily = FakeDailyRepository().apply {
            setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
            setHistory(
                game,
                listOf(DailyEntry(game, day, 7, "alpha/v1", 0L), DailyEntry(game, day.minusDays(1), 6, "alpha/v1", 0L)),
            )
        }
        val koin = startGraph(FakeGames(listOf(game)), daily)
        try {
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(game.value, resultDay.toString(), 12_340, 0),
                        onPlayAgain = {},
                        onNext = {},
                        onDone = {},
                        onOpenBoard = onOpenBoard,
                    )
                }
            }
            composeRule.waitForIdle()
            check()
        } finally {
            koin.close()
        }
    }

    private fun startGraph(fakes: FakeGames, daily: FakeDailyRepository): KoinApplication {
        stopKoin() // tests share the JVM-global Koin context
        return startKoin {
            modules(
                commonModule,
                module {
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<GameCalendar> { FakeGameCalendar(day) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { GetPuzzleDay(get()) }
                    factory { ComputeStreak(get()) }
                    factory { ObserveGameStats(get(), get(), get()) }
                    factory { GetNextRollover(get()) }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                    single { SubmitStatusTracker() }
                    single<CustomBoardRepository> { FakeCustomBoardRepository() }
                    single { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get(), get(), get()) }
                    factory { ObserveResultRank(get(), get()) }
                    factory { ObserveResultInsights(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }
}
