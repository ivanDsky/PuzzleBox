package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.ComputeStreak
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
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
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
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
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
 * Ticket 23: the win-moment coverage on the result ROUTE. The A3 facts render (time hero,
 * the hints tile), the A10 pill is conditional on the in-memory verdict — absent while the
 * write is in flight (fire-and-observe, never waited on) — and "Play again" and Share hand
 * back to navigation. Ticket 17 / S08: each primary step (Next, a replay's Done) shows its
 * own tag, and once everything is solved the countdown stands in for the button. A practice
 * puzzle shows no pill and no streak. Assertions ride tags, never copy.
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
        writeFailed: Boolean = false,
        primary: ResultPrimaryAction? = ResultPrimaryAction.Done,
        practice: Boolean = false,
        streak: Int? = null,
        rolloverAt: Instant? = null,
        onPrimary: () -> Unit = {},
        onShare: () -> Unit = {},
        onPlayAgain: (() -> Unit)? = {},
        onRetryWrite: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultScreen(
                    timeMs = 95_000,
                    hintsUsed = 1,
                    primary = primary,
                    onPrimary = onPrimary,
                    onShare = onShare,
                    onPlayAgain = onPlayAgain,
                    game = "Alpha",
                    puzzleNumber = if (practice) null else 7,
                    practice = practice,
                    verdict = verdict,
                    previousBest = previousBestMs?.let { BestScore(it, previousHints) },
                    rankOrder = rankOrder,
                    streak = streak,
                    doneToday = 1,
                    total = 2,
                    rolloverAt = rolloverAt,
                    writeFailed = writeFailed,
                    onRetryWrite = onRetryWrite,
                )
            }
        }
    }

    @Test
    fun `shows the header, the time and the tiles`() {
        setContent(streak = 3)
        listOf(
            "result.screen", "result.stamp", "result.label", "result.time",
            "result.tile.hints", "result.tile.streak", "result.tile.today",
        ).forEach { composeRule.onNodeWithTag(it).assertExists() }
    }

    @Test
    fun `an unknown streak hides its tile`() {
        setContent(streak = null)
        composeRule.onNodeWithTag("result.tile.streak").assertDoesNotExist()
        composeRule.onNodeWithTag("result.tile.hints").assertExists()
    }

    @Test
    fun `new best pill appears on a faster solve with the gap it beat`() {
        setContent(verdict = RecordVerdict.NewBest, previousBestMs = 120_000)
        composeRule.onNodeWithTag("result.record_badge").assertExists()
        composeRule.onNodeWithTag("result.previous_best").assertExists()
    }

    @Test
    fun `first solve pill appears on the game's first completion`() {
        setContent(verdict = RecordVerdict.FirstSolve)
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
    fun `not a new best shows no pill and the best to chase`() {
        setContent(verdict = RecordVerdict.NotBest, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.previous_best").assertExists()
    }

    @Test
    fun `a tie shows the pill and no second best line`() {
        setContent(verdict = RecordVerdict.TiedBest, previousBestMs = 95_000)
        composeRule.onNodeWithTag("result.record_badge").assertExists()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
    }

    @Test
    fun `a replay claims no pill and says it was a replay`() {
        setContent(verdict = RecordVerdict.Replay, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.previous_best").assertExists()
    }

    @Test
    fun `a practice puzzle shows no streak tile and no new best`() {
        setContent(practice = true, verdict = RecordVerdict.NewBest, previousBestMs = 120_000, streak = 3)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.tile.streak").assertDoesNotExist()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
        composeRule.onNodeWithTag("result.tile.hints").assertExists()
    }

    @Test
    fun `a hints-first game leads its best line with the hints`() {
        setContent(verdict = RecordVerdict.NotBest, previousBestMs = 102_000, rankOrder = RankOrder.HintsThenTime)
        composeRule.onNodeWithText("Your best is Perfect · 1:42").assertExists()
    }

    @Test
    fun `a hints-first new best shows the score it beat`() {
        setContent(verdict = RecordVerdict.NewBest, previousBestMs = 80_000, previousHints = 1, rankOrder = RankOrder.HintsThenTime)
        composeRule.onNodeWithText("Previous best 1 hint · 1:20").assertExists()
    }

    @Test
    fun `a time-first best line never shows hints`() {
        setContent(verdict = RecordVerdict.NotBest, previousBestMs = 80_000, previousHints = 2)
        composeRule.onNodeWithText("Your best is 1:20").assertExists()
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
    fun `next names the next game and hands back`() {
        var taps = 0
        setContent(primary = ResultPrimaryAction.Next(GameId("beta"), "Beta"), onPrimary = { taps++ })
        composeRule.onNodeWithTag("result.done").assertDoesNotExist()
        composeRule.onNodeWithTag("result.all_done").assertDoesNotExist()
        composeRule.onNodeWithTag("result.playAgain").assertExists()
        composeRule.onNodeWithTag("result.next").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun `with every game solved the countdown replaces next`() {
        setContent(primary = ResultPrimaryAction.AllDone, rolloverAt = Instant.now().plusSeconds(3_600))
        composeRule.onNodeWithTag("result.all_done").assertExists()
        composeRule.onNodeWithTag("result.countdown").assertExists()
        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
        composeRule.onNodeWithTag("result.done").assertDoesNotExist()
        composeRule.onNodeWithTag("result.share").assertExists()
    }

    @Test
    fun `a replay's result offers done and play again`() {
        setContent(primary = ResultPrimaryAction.Done)
        composeRule.onNodeWithTag("result.done").assertExists()
        composeRule.onNodeWithTag("result.playAgain").assertExists()
        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
        composeRule.onNodeWithTag("result.all_done").assertDoesNotExist()
    }

    @Test
    fun `an unresolved next step exposes no actions to tap or read`() {
        var taps = 0
        setContent(primary = null, onPrimary = { taps++ }, onShare = { taps++ }, onPlayAgain = { taps++ })
        composeRule.onNodeWithTag("result.time").assertExists()
        listOf("result.next", "result.done", "result.share", "result.playAgain", "result.all_done").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }
        composeRule.runOnIdle { assertEquals(0, taps) }
    }

    @Test
    fun `no play again when none is handed in`() {
        setContent(onPlayAgain = null)
        composeRule.onNodeWithTag("result.playAgain").assertDoesNotExist()
        composeRule.onNodeWithTag("result.share").assertExists()
    }

    @Test
    fun `a failed record write stays surfaced and retryable`() {
        var retried = 0
        setContent(writeFailed = true, onRetryWrite = { retried++ })
        composeRule.onNodeWithTag("result.write_failed").assertExists()
        composeRule.onNodeWithTag("result.retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }
    }

    @Test
    fun `no write failure affordance while the write is healthy`() {
        setContent(writeFailed = false)
        composeRule.onNodeWithTag("result.write_failed").assertDoesNotExist()
    }

    /**
     * The route wiring end-to-end on the store: the slot's solved live play stamps the
     * pill (the write is fire-and-observe — the surface never waits for it), Up hands back
     * to navigation, the only game solved shows the countdown, and "Play again" hands back
     * so the game's reopen resets the SAME puzzle to a fresh session.
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
                    )
                }
            }
            composeRule.onNodeWithTag("result.screen").assertExists()
            // The verdict is in memory: the game's first solve pill renders.
            composeRule.onNodeWithTag("result.record_badge").assertExists()
            composeRule.onNodeWithTag("result.label").assertExists()
            composeRule.onNodeWithTag("result.tile.today").assertExists()

            composeRule.onNodeWithTag("result.up").performClick()
            composeRule.runOnIdle { assertEquals(1, done) }

            // The only game is solved: the countdown stands where Next would.
            composeRule.onNodeWithTag("result.all_done").assertExists()
            composeRule.onNodeWithTag("result.countdown").assertExists()
            composeRule.onNodeWithTag("result.next").assertDoesNotExist()

            composeRule.onNodeWithTag("result.playAgain").performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                store.open(gameKey)
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
                    )
                }
            }
            composeRule.onNodeWithTag("result.time").assertExists()
            composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
            composeRule.onNodeWithTag("result.write_failed").assertDoesNotExist()
        } finally {
            koin.close()
        }
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
                    )
                }
            }
            composeRule.onNodeWithTag("result.next").performClick()
            composeRule.runOnIdle { assertEquals(TestGames.beta, next) }
        } finally {
            koin.close()
        }
    }

    private fun startGraph(
        fakes: FakeGames,
        daily: FakeDailyRepository,
        flags: ReleaseFlags = FakeReleaseFlags(),
    ): KoinApplication {
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
                    single<GameCalendar> { FakeGameCalendar(day) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { GetPuzzleDay(get()) }
                    factory { ComputeStreak(get()) }
                    factory { ObserveGameStats(get(), get(), get()) }
                    factory { GetNextRollover(get()) }
                    single { flags }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                    single<CustomBoardRepository> { FakeCustomBoardRepository() }
                    single { dismissedGoogleSignIn }
                    factory { ObserveLeaderboard(get(), get()) }
                    factory { ObserveResultRank(get()) }
                    factory { ObserveResultInsights(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }
}
