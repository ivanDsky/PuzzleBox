package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.LocalDate
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
 * result ROUTE. The A3 facts render (time hero, hints), the record badge is conditional
 * on the in-memory verdict — present when stamped, absent while the write is in flight
 * (fire-and-observe, never waited on) — and "Play again" hands back to navigation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    private fun setContent(
        isRecord: Boolean? = null,
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
                    isRecord = isRecord,
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
    fun `record badge appears when stamped`() {
        setContent(isRecord = true)
        composeRule.onNodeWithTag("result.record_badge").assertExists()
        composeRule.onNodeWithText("New personal record").assertExists()
    }

    @Test
    fun `record badge absent while the write is still in flight`() {
        setContent(isRecord = null)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
    }

    @Test
    fun `record badge absent when the play is not a record`() {
        setContent(isRecord = false)
        composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()
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
     * The route wiring end-to-end on the flow: solving stamps the badge in memory (the
     * write is fire-and-observe — the surface never waits for it), and "Play again"
     * resets the SAME puzzle to a fresh session before handing back to navigation.
     */
    @Test
    fun `result route reads the flow's live state and replays the same puzzle`() {
        val daily = freshDailyForAllGames().apply {
            setHistory(ZipDefinition.id, listOf(PlayTestSupport.entry(day)))
        }
        val completions = FakeCompletionRepository()
        val flow = PlayFlow(daily = daily, completions = completions)
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin { modules(module { single { flow } }) }
        try {
            var replayed = false
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(timeMs = 12_340, hintsUsed = 0, onPlayAgain = { replayed = true })
                }
            }
            // The flow has no state yet (process-death route restore case): the frozen
            // facts from the key render, no badge can be claimed.
            composeRule.onNodeWithText("Solved!").assertExists()
            composeRule.onNodeWithText("New personal record").assertDoesNotExist()

            // Bring the flow to a solved state the way a play-through would.
            composeRule.runOnIdle {
                val state = flow.start(PlayGame.Zip, onSessionChanged = {})
                state.load()
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val playState = flow.state as ZipPlayState
                PlayTestSupport.solution.forEach { cell ->
                    playState.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
                }
            }
            composeRule.waitForIdle()
            // The verdict is in memory: the badge renders; the facts still show.
            composeRule.onNodeWithTag("result.record_badge").assertExists()

            // "Play again": same puzzle, fresh session, then navigation is handed back.
            composeRule.onNodeWithTag("result.play_again").performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                val state = flow.state as ZipPlayState
                assertTrue(state.session?.path?.isEmpty() == true)
                assertTrue(state.session?.isComplete == false)
            }
        } finally {
            koin.close()
        }
    }
}
