package com.ivandsky.puzzlebox.app.queens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.nav.Queens
import com.ivandsky.puzzlebox.app.puzzleBoxEntryProvider
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.QueensDailyEntry
import com.ivandsky.puzzlebox.data.repository.QueensDailyRepository
import com.ivandsky.puzzlebox.data.repository.QueensToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.play.QueensPlayFlow
import com.ivandsky.puzzlebox.play.QueensRoute
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    private class FakeQueensRepository(private val fresh: QueensToday, private val history: List<QueensDailyEntry>) : QueensDailyRepository {
        override suspend fun today(): QueensToday = fresh
        override suspend fun cached(day: LocalDate): Puzzle? = null
        override fun observeHistory(): Flow<List<QueensDailyEntry>> = flowOf(history)
    }

    @Test
    fun `queens route renders the DI-bound board and a completion records a history entry`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        koinApp.koin.loadModules(
            listOf(
                module {
                    single<QueensDailyRepository> {
                        FakeQueensRepository(
                            fresh = QueensToday.Fresh(envelope()),
                            history = listOf(
                                QueensDailyEntry(
                                    LocalDate.now((QueensDefinition.cadence as Cadence.Daily).zone),
                                    42,
                                    "queens/n8",
                                    0L,
                                ),
                            ),
                        )
                    }
                },
            ),
            allowOverride = true,
        )
        val flow = koinApp.koin.get<QueensPlayFlow>()

        composeRule.setContent {
            PuzzleBoxTheme {
                QueensRoute(layout = PlayLayout.SinglePane, onBack = {})
            }
        }

        composeRule.onNodeWithText("Queens").assertExists()
        composeRule.onNodeWithTag("queens.board").assertExists()

        // The board is bound to a real session off the graph, with today's ref.
        composeRule.waitUntil(10_000) { flow.state?.board != null }
        val board = flow.state!!.board!!
        assertNotNull(board.session.ref)

        // Complete the game through the board's §5 tap cycle (two taps per queen).
        composeRule.runOnIdle {
            for (idx in listOf(0, 12, 23, 29, 34, 46, 49, 59)) {
                board.onTap(idx)
                board.onTap(idx)
            }
        }
        composeRule.waitUntil(10_000) { flow.state!!.board!!.session.isComplete }

        // R-3 (fix round 4): on the real screen, the solved state presents fullscreen
        // over the play surface — overlay bounds cover the whole window, no game chrome
        // beside it (the old board-pane overlay was cut off by the game content).
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("queens/win_overlay").fetchSemanticsNodes().isNotEmpty()
        }
        val overlayBounds = composeRule.onAllNodesWithTag("queens/win_overlay")
            .fetchSemanticsNodes().single().size
        // R-5: the win presents MODALLY in its own dialog window — the overlay covers
        // every window root (the play surface behind it and the modal window itself).
        val rootBoundsList = composeRule.onAllNodes(
            androidx.compose.ui.test.isRoot(),
        ).fetchSemanticsNodes().map { it.size }
        assertTrue(
            "win overlay covers the window ($overlayBounds vs $rootBoundsList)",
            rootBoundsList.all {
                overlayBounds.width >= it.width && overlayBounds.height >= it.height
            },
        )

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

    @Test
    fun `the queens nav key resolves in the route table`() {
        val entry = puzzleBoxEntryProvider(
            backStack = androidx.navigation3.runtime.NavBackStack(Queens),
            homeLayout = com.ivandsky.puzzlebox.home.HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = com.ivandsky.puzzlebox.history.HistoryLayout.SinglePane,
            themeMode = com.ivandsky.puzzlebox.model.ThemeMode.SYSTEM,
            onSetThemeMode = {},
        ).invoke(Queens)
        assertTrue(entry.contentKey.toString().contains("Queens"))
    }
}
