package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
        onPlayAgain: () -> Unit = {},
        onRetryWrite: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultScreen(
                    timeMs = 95_000,
                    hintsUsed = 1,
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
        composeRule.onNodeWithText("01:35").assertExists()
        composeRule.onNodeWithText("1 hint").assertExists()
    }

    @Test
    fun `new best badge appears on a faster solve with the time it beat`() {
        setContent(verdict = RecordVerdict.NewBest, previousBestMs = 120_000)
        composeRule.onNodeWithTag("result.record_badge").assertExists()
        composeRule.onNodeWithText("New best").assertExists()
        composeRule.onNodeWithText("Previous best 02:00").assertExists()
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
        composeRule.onNodeWithText("Best 01:20").assertExists()
    }

    @Test
    fun `play again hands back to navigation`() {
        var replayed = 0
        setContent(onPlayAgain = { replayed++ })
        composeRule.onNodeWithTag("result.play_again").performClick()
        composeRule.runOnIdle { assertEquals(1, replayed) }
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
     * The route wiring end-to-end on the store: solving stamps the badge in memory (the
     * write is fire-and-observe — the surface never waits for it), and "Play again"
     * resets the SAME puzzle to a fresh session before handing back to navigation.
     */
    @Test
    fun `result route reads the store's live play and replays the same puzzle`() {
        val fakes = FakeGames(listOf(game))
        val daily = FakeDailyRepository().apply {
            setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
            setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
        }
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin {
            modules(
                module {
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<com.ivandsky.puzzlebox.data.repository.DailyRepository> { daily }
                    single<com.ivandsky.puzzlebox.data.repository.CompletionRepository> { FakeCompletionRepository() }
                    single<com.ivandsky.puzzlebox.data.repository.KeptSessionRepository> { FakeKeptSessionRepository() }
                    factory { com.ivandsky.puzzlebox.domain.ObserveTodaySet(get(), get(), get()) }
                },
                playModule,
            )
        }
        val store = koin.koin.get<PlaySessionStore>()
        try {
            var replayed = false
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(timeMs = 12_340, hintsUsed = 0, onPlayAgain = { replayed = true })
                }
            }
            // No live play yet (process-death route restore case): the frozen facts
            // from the key render, no badge can be claimed.
            composeRule.onNodeWithText("Solved!").assertExists()
            composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()

            // Bring the store to a solved play the way a play-through would.
            composeRule.runOnIdle { store.start(game, onSessionChanged = {})!!.load() }
            composeRule.waitForIdle()
            composeRule.runOnIdle { fakes[game].latest.win() }
            composeRule.waitForIdle()
            // The verdict is in memory: the game's first solve badge renders; the label shows.
            composeRule.onNodeWithText("First solve").assertExists()
            composeRule.onNodeWithText("Alpha · #7").assertExists()

            // "Play again": same puzzle, fresh session, then navigation is handed back.
            composeRule.onNodeWithTag("result.play_again").performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                val controller = fakes[game].latest
                assertEquals(1, controller.replays)
                assertEquals(0, controller.moves)
                assertTrue(!controller.isComplete)
            }
        } finally {
            koin.close()
        }
    }
}
