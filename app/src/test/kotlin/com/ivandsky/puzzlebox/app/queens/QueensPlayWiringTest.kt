package com.ivandsky.puzzlebox.app.queens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.testing.FakeDailyRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.play.PlayFlow
import com.ivandsky.puzzlebox.play.PlayRoute
import com.ivandsky.puzzlebox.play.QueensPlayState
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * queens-10 DoD wiring test: launching the Play path for gameId=queens renders the
 * queens board bound to a real session from the Koin graph, and completing the game
 * records a history entry through the app's real CompletionRepository (the same
 * emitter → repository path Zip's play uses).
 *
 * The daily feed is the only overridden binding (Robolectric must not touch the
 * network); Room, the flow, and the registry lines are the real graph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class QueensPlayWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        stopKoin()
    }

    /** 8×8 fixture, one queen per row/column/region on the main diagonal. */
    private fun envelope(): Puzzle {
        val puzzle = QueensPuzzle(
            size = 8,
            regions = List(64) { it / 8 },
            solution = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell),
        )
        return Puzzle(
            gameId = QueensDefinition.id,
            variant = queensVariantId(8),
            formatVersion = QueensCodec.VERSION,
            payload = QueensCodec.encode(puzzle),
        )
    }

    @Test
    fun `the play tab offers queens, and a completed queens game records a history entry`() {
        stopKoin() // tests share the JVM-global Koin context
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        koinApp.koin.loadModules(
            listOf(
                module {
                    single<DailyRepository> {
                        FakeDailyRepository().apply {
                            setToday(QueensDefinition.id, DailyToday.Fresh(envelope()))
                            setHistory(
                                QueensDefinition.id,
                                listOf(DailyEntry(QueensDefinition.id, QueensDefinition.cadence.today(), 42, "queens/n8", 0L)),
                            )
                        }
                    }
                },
            ),
            allowOverride = true,
        )
        val flow = koinApp.koin.get<PlayFlow>()

        val solved = ArrayList<Pair<Long, Int>>()
        composeRule.setContent {
            PuzzleBoxTheme {
                PlayRoute(
                    layout = PlayLayout.SinglePane,
                    onBack = {},
                    onSolved = { timeMs, hintsUsed -> solved.add(timeMs to hintsUsed) },
                )
            }
        }

        // The Play tab lands on the shared game picker, Queens among its games — never
        // straight on a Queens board.
        composeRule.onNodeWithTag("play.picker").assertExists()
        composeRule.onNodeWithTag("play.picker_queens").performClick()
        composeRule.onNodeWithTag("queens.board").assertExists()

        // The board is bound to a real session off the graph, with today's ref.
        composeRule.waitUntil(10_000) { (flow.state as? QueensPlayState)?.board != null }
        val board = (flow.state as QueensPlayState).board!!
        assertNotNull(board.session.ref)

        // Complete the game through the board's §5 tap cycle (two taps per queen).
        composeRule.runOnIdle {
            for (idx in listOf(0, 12, 23, 29, 34, 46, 49, 59)) {
                board.onTap(idx)
                board.onTap(idx)
            }
        }
        composeRule.waitUntil(10_000) { (flow.state as QueensPlayState).board!!.session.isComplete }

        // Fix round 6 (F-3/R-5): the win is a ROUTE handoff — the play screen invokes
        // onSolved exactly once with the frozen facts; no win overlay exists anymore.
        composeRule.waitUntil(10_000) {
            // A bare waitUntil on a non-Compose condition doesn't drive a frame in the
            // Robolectric idling scheduler — pump one so the completedAt-keyed effect
            // can run (device recomposes via the choreographer; the test must poke it).
            composeRule.runOnIdle { }
            solved.isNotEmpty()
        }
        composeRule.runOnIdle { }
        assertEquals("one handoff on the win transition", 1, solved.size)
        assertTrue("frozen elapsed time rides the handoff", solved.single().first >= 0L)
        assertEquals("zero hints used", 0, solved.single().second)
        composeRule.onAllNodesWithTag("queens/win_overlay").assertCountEquals(0)

        // The record write landed through the app's real CompletionRepository. The
        // emitter is fire-and-observe on the flow's Main scope, so pump the main looper
        // (runOnIdle) between bounded polls for the Room row.
        val completions = koinApp.koin.get<CompletionRepository>()
        var records: List<com.ivandsky.puzzlebox.game.CompletionRecord> = emptyList()
        repeat(50) {
            composeRule.runOnIdle { } // pump the main-looper queue (emitter continuation)
            records = runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(100L) {
                    completions.observeRecords().first { it.isNotEmpty() }
                }
            } ?: emptyList()
            if (records.isNotEmpty()) return@repeat
        }
        assertTrue("expected a recorded completion, got none", records.isNotEmpty())
    }
}
