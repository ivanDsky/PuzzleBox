package com.ivandsky.puzzlebox.app.queens

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.navigation.playEntry
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
 * queens-10 DoD wiring test: picking Queens on the Play tab's hub renders the queens
 * board bound to a real session from the Koin graph; completing the game replaces the
 * board with its result and records a history entry through the app's real
 * CompletionRepository (the same emitter → repository path Zip's play uses).
 *
 * The daily feed and the seen-tutorial prefs are the only overridden bindings
 * (Robolectric must not touch the network, and the board opens without the lessons);
 * Room, the flow, and the registry lines are the real graph.
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
                    // Every tutorial seen: this path is about the board; the lessons have their own tests.
                    single<com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository> {
                        com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository(
                            seen = setOf(
                                com.ivandsky.puzzlebox.games.zip.ZipDefinition.id,
                                com.ivandsky.puzzlebox.games.tango.TangoDefinition.id,
                                QueensDefinition.id,
                            ),
                        )
                    }
                },
            ),
            allowOverride = true,
        )
        val store = koinApp.koin.get<PlaySessionStore>()
        val slot = PlaySlot(QueensDefinition.id, QueensDefinition.cadence.today())
        fun queens() = store.peek(slot)?.controller as? QueensPlayController

        val navigator = Navigator(NavigationState(tab = PlayKey, stacks = listOf(listOf(PlayKey))))
        composeRule.setContent {
            PuzzleBoxTheme {
                NavDisplay(
                    backStack = navigator.state.currentStack,
                    onBack = { navigator.pop() },
                    entryProvider = entryProvider {
                        playEntry(navigator, PlayLayout.SinglePane)
                        entry<ResultKey> { Text("result") }
                    },
                )
            }
        }
        fun solved() = composeRule.runOnIdle { navigator.state.currentStack.filterIsInstance<ResultKey>() }

        // The Play tab lands on the shared games hub, Queens among its games — never
        // straight on a Queens board.
        composeRule.onNodeWithTag("route.hub").assertExists()
        composeRule.onNodeWithTag("hub.row.queens").performClick()
        // Opening reads the saved-play row off the real Room graph first: the board lands async.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("queens.board").fetchSemanticsNodes().isNotEmpty()
        }

        // The board is bound to a real session off the graph, with today's ref.
        composeRule.waitUntil(10_000) { queens()?.board != null }
        val board = queens()!!.board!!
        assertNotNull(board.session.ref)

        // Complete the game through the board's §5 tap cycle (two taps per queen).
        composeRule.runOnIdle {
            for (idx in listOf(0, 12, 23, 29, 34, 46, 49, 59)) {
                board.onTap(idx)
                board.onTap(idx)
            }
        }
        composeRule.waitUntil(10_000) { queens()!!.board!!.session.isComplete }

        // Fix round 6 (F-3/R-5): the win is a ROUTE handoff — the board is replaced by
        // exactly one result carrying the frozen facts; no win overlay exists anymore.
        composeRule.waitUntil(10_000) {
            // A bare waitUntil on a non-Compose condition doesn't drive a frame in the
            // Robolectric idling scheduler — pump one so the completedAt-keyed effect
            // can run (device recomposes via the choreographer; the test must poke it).
            composeRule.runOnIdle { }
            solved().isNotEmpty()
        }
        composeRule.runOnIdle { }
        assertEquals("one handoff on the win transition", 1, solved().size)
        assertTrue("frozen elapsed time rides the handoff", solved().single().timeMs >= 0L)
        assertEquals("zero hints used", 0, solved().single().hintsUsed)
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
