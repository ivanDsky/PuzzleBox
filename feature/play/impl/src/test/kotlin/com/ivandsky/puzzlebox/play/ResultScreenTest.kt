package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GameRegistry
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
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
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
 * Ticket 23: the win-moment coverage, rewritten from the deleted WinOverlay to the
 * result ROUTE. The A3 facts render (time hero, hints), the A10 badge is conditional
 * on the in-memory verdict — "New best" / "First solve", absent while the write is in flight
 * (fire-and-observe, never waited on) — and "Play again" hands back to navigation.
 * Ticket 17: each primary action (Next, All done, a replay's Done) shows its own tag.
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
        writeFailed: Boolean = false,
        primary: ResultPrimaryAction? = ResultPrimaryAction.Done,
        onPrimary: () -> Unit = {},
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
                    onPlayAgain = onPlayAgain,
                    verdict = verdict,
                    previousBestMs = previousBestMs,
                    writeFailed = writeFailed,
                    onRetryWrite = onRetryWrite,
                )
            }
        }
    }

    @Test
    fun `shows normalized a3 facts`() {
        setContent()
        composeRule.onNodeWithText("Solved!").assertExists()
        composeRule.onNodeWithText("1:35").assertExists()
        composeRule.onNodeWithText("1 hint").assertExists()
    }

    @Test
    fun `new best badge appears on a faster solve with the time it beat`() {
        setContent(verdict = RecordVerdict.NewBest, previousBestMs = 120_000)
        composeRule.onNodeWithTag("result.record_badge").assertExists()
        composeRule.onNodeWithText("New best").assertExists()
        composeRule.onNodeWithText("Previous best 2:00").assertExists()
    }

    @Test
    fun `first solve badge appears on the game's first completion`() {
        setContent(verdict = RecordVerdict.FirstSolve)
        composeRule.onNodeWithText("First solve").assertExists()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
    }

    @Test
    fun `record badge absent while the write is still in flight`() {
        setContent(verdict = null, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
    }

    @Test
    fun `not a new best shows no badge and the previous best`() {
        setContent(verdict = RecordVerdict.NotBest, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
        composeRule.onNodeWithText("Best 1:20").assertExists()
    }

    @Test
    fun `a tie shows the tie badge and no second best line`() {
        setContent(verdict = RecordVerdict.TiedBest, previousBestMs = 95_000)
        composeRule.onNodeWithText("Tied your best!").assertExists()
        composeRule.onNodeWithTag("result.previous_best").assertDoesNotExist()
    }

    @Test
    fun `a replay claims no badge and says it was a replay beside the best`() {
        setContent(verdict = RecordVerdict.Replay, previousBestMs = 80_000)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
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
    fun `next names the next game and hands back`() {
        var taps = 0
        setContent(primary = ResultPrimaryAction.Next(GameId("beta"), "Beta"), onPrimary = { taps++ })
        composeRule.onNodeWithText("Next: Beta").assertExists()
        composeRule.onNodeWithTag("result.done").assertDoesNotExist()
        composeRule.onNodeWithTag("result.all_done").assertDoesNotExist()
        composeRule.onNodeWithTag("result.playAgain").assertExists()
        composeRule.onNodeWithTag("result.next").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun `all done shows the copy and done`() {
        var taps = 0
        setContent(primary = ResultPrimaryAction.AllDone, onPrimary = { taps++ })
        composeRule.onNodeWithText("All done for today").assertExists()
        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
        composeRule.onNodeWithTag("result.done").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
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
        setContent(primary = null, onPrimary = { taps++ }, onPlayAgain = { taps++ })
        composeRule.onNodeWithText("Solved!").assertExists()
        listOf("result.next", "result.done", "result.playAgain", "result.all_done").forEach {
            composeRule.onNodeWithTag(it).assertDoesNotExist()
        }
        composeRule.runOnIdle { assertEquals(0, taps) }
    }

    @Test
    fun `no play again when none is handed in`() {
        setContent(onPlayAgain = null)
        composeRule.onNodeWithTag("result.playAgain").assertDoesNotExist()
    }

    @Test
    fun `a failed record write stays surfaced and retryable`() {
        var retried = 0
        setContent(writeFailed = true, onRetryWrite = { retried++ })
        composeRule.onNodeWithTag("result.write_failed").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }
    }

    @Test
    fun `no write failure affordance while the write is healthy`() {
        setContent(writeFailed = false)
        composeRule.onNodeWithTag("result.write_failed").assertDoesNotExist()
    }

    /**
     * The route wiring end-to-end on the store: the slot's solved live play stamps the
     * badge and label (the write is fire-and-observe — the surface never waits for it),
     * Up hands back to navigation, and "Play again" hands back so the game's reopen
     * resets the SAME puzzle to a fresh session.
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
            composeRule.onNodeWithText("Solved!").assertExists()
            // The verdict is in memory: the game's first solve badge renders.
            composeRule.onNodeWithText("First solve").assertExists()
            composeRule.onNodeWithText("Alpha · #7").assertExists()

            composeRule.onNodeWithTag("result.up").performClick()
            composeRule.runOnIdle { assertEquals(1, done) }

            // The only game is solved: all done, and Done pops too.
            composeRule.onNodeWithText("All done for today").assertExists()
            composeRule.onNodeWithTag("result.done").performClick()
            composeRule.runOnIdle { assertEquals(2, done) }

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
    fun `result route without a live play renders the key's facts and no badge`() {
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
            composeRule.onNodeWithText("Solved!").assertExists()
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
                    single<GameCalendar> { FakeGameCalendar(day) }
                    factory { GetReplaySession(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }
}
