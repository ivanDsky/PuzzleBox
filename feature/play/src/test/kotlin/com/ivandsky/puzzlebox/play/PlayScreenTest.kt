package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.data.repository.ZipToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.Board
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric compose coverage for the play screen (A17): states, chrome, and the
 * ticket-23 solved handoff (the win moment itself lives on the result route now).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The (timeMs, hintsUsed) handed to the result route, captured per test. */
    private var solvedFacts: Pair<Long, Int>? = null

    private val day: LocalDate = LocalDate.of(2026, 10, 31)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun daily(
        result: ZipToday = ZipToday.Fresh(PlayTestSupport.envelope()),
        history: List<ZipDailyEntry> = listOf(ZipDailyEntry(day, 592, "zip/n5", 0L)),
    ) = FakeZipDailyRepository(result, history)

    private fun readyState(
        layout: PlayLayout = PlayLayout.SinglePane,
        repo: FakeZipDailyRepository = daily(),
        completions: com.ivandsky.puzzlebox.data.repository.CompletionRepository = FakeCompletionRepository(),
    ): ZipPlayState {
        val state = ZipPlayState(
            daily = repo,
            completions = completions,
            scope = scope,
            today = { day },
        )
        state.load()
        solvedFacts = null
        composeRule.setContent {
            PuzzleBoxTheme {
                PlayScreen(
                    state = state,
                    layout = layout,
                    registry = GameUiRegistry().apply { registerZipBoard() },
                    onBack = {},
                    onSolved = { timeMs, hintsUsed -> solvedFacts = timeMs to hintsUsed },
                )
            }
        }
        return state
    }

    private fun bareState(repo: FakeZipDailyRepository): ZipPlayState = ZipPlayState(
        daily = repo,
        completions = FakeCompletionRepository(),
        scope = scope,
        today = { day },
    )

    private fun setContent(state: ZipPlayState, layout: PlayLayout = PlayLayout.SinglePane, registry: GameUiRegistry = GameUiRegistry().apply { registerZipBoard() }) {
        solvedFacts = null
        composeRule.setContent {
            PuzzleBoxTheme {
                PlayScreen(state, layout, registry, onBack = {}, onSolved = { timeMs, hintsUsed -> solvedFacts = timeMs to hintsUsed })
            }
        }
    }

    @Test
    fun `loading state renders the board loading indicator`() {
        setContent(bareState(daily()))
        composeRule.onNodeWithTag("play.loading").assertExists()
    }

    @Test
    fun `unavailable state renders the error with a working retry`() {
        val repo = daily(result = ZipToday.Unavailable(FetchError.Unavailable("no net")))
        val state = bareState(repo)
        state.load()
        setContent(state)
        composeRule.onNodeWithTag("play.error").assertExists()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { check(repo.todayCalls >= 2) { "retry did not refetch" } }
    }

    @Test
    fun `ready board renders the play chrome`() {
        readyState()
        composeRule.onNodeWithTag("play.board").assertExists()
        composeRule.onNodeWithTag("play.side_panel").assertExists()
        composeRule.onNodeWithText("Zip").assertExists()
        composeRule.onNodeWithText("Daily · #592").assertExists()
        composeRule.onNodeWithText("Hint").assertExists()
        composeRule.onNodeWithText("00:00").assertExists()
        // Ticket 22: the start waypoint is a dot with its bold label INSIDE it — bare
        // corner numbers are gone.
        composeRule.onNodeWithTag("play.waypoint_dot_0").assertExists()
        composeRule.onNodeWithText("1").assertExists()
        composeRule.onNodeWithText("Solved!").assertDoesNotExist()
    }

    @Test
    fun `degraded ready shows the banner but stays playable`() {
        readyState(repo = daily(result = ZipToday.Cached(PlayTestSupport.envelope(), FetchError.Unavailable("offline"))))
        composeRule.onNodeWithTag("play.degrade_banner").assertExists()
        composeRule.onNodeWithText("Offline — playing today's saved puzzle.").assertExists()
        composeRule.onNodeWithTag("play.board").assertExists()
    }

    @Test
    fun `hint button counts up and highlights a cell`() {
        val state = readyState()
        composeRule.onNodeWithText("Hint").performClick()
        composeRule.runOnIdle { check(state.session?.hintsUsed == 1) { "hint not counted" } }
        composeRule.onNodeWithText("Hint · 1").assertExists()
        composeRule.onNodeWithTag("play.hint_cell").assertExists()
    }

    @Test
    fun `hint autoplace shows the arrow at the new head`() {
        val state = readyState()
        composeRule.runOnIdle { state.onDragEvent(DragEvent.Extend(GridCell(0, 0))) }
        // Manual clock: apply one frame for the placed arrow, then run out its ~2s hold
        // + fade and confirm it clears. Representation: the plain centered arrowhead
        // glyph (ticket 28 r5) under the play.hint_arrow tag.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Hint").performClick()
        composeRule.runOnIdle { check(state.hintArrow != null) { "hint did not place an arrow" } }
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.onNodeWithTag("play.hint_arrow").assertExists()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.onNodeWithTag("play.hint_arrow").assertDoesNotExist()
    }

    @Test
    fun `solving hands the frozen facts to the result route once`() {
        val state = readyState()
        composeRule.runOnIdle {
            PlayTestSupport.solution.forEach { cell ->
                state.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
            }
        }
        composeRule.waitForIdle()
        // Ticket 23: the win moment is a route handoff, not an overlay — the screen
        // carries the frozen A3 facts (time, hints) and never renders win content.
        val facts = solvedFacts
        check(facts != null) { "solving did not hand off to the result route" }
        check(facts.first >= 0) { "negative time handed off" }
        check(facts.second == 0) { "no hints were used on this solve" }
        composeRule.onNodeWithText("Solved!").assertDoesNotExist()
        composeRule.onNodeWithTag("play.board").assertExists()
    }

    @Test
    fun `a fresh entry does not hand off to the result route`() {
        readyState()
        composeRule.waitForIdle()
        check(solvedFacts == null) { "unsolved session handed off to the result route" }
    }

    @Test
    fun `replay resets to a fresh board and can hand off again`() {
        val state = readyState()
        composeRule.runOnIdle {
            PlayTestSupport.solution.forEach { cell ->
                state.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
            }
        }
        composeRule.waitForIdle()
        check(solvedFacts != null) { "first solve did not hand off" }
        composeRule.runOnIdle { state.replay() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("00:00").assertExists()
        composeRule.runOnIdle { check(state.session?.path?.isEmpty() == true) }

        composeRule.runOnIdle {
            PlayTestSupport.solution.forEach { cell ->
                state.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
            }
        }
        composeRule.waitForIdle()
        check(solvedFacts != null) { "second solve did not hand off" }
    }

    @Test
    fun `two pane arrangement renders board and side panel`() {
        readyState(layout = PlayLayout.TwoPane)
        composeRule.onNodeWithTag("play.board").assertExists()
        composeRule.onNodeWithTag("play.side_panel").assertExists()
    }

    /** A4: the board is capped at [PlayBoardMaxWidth], centered in its pane, aspect exact. */
    @Test
    fun `wide pane caps the board at the max width and centers it`() {
        val state = bareState(daily())
        state.load()
        var density = Density(1f)
        composeRule.setContent {
            PuzzleBoxTheme {
                density = LocalDensity.current
                Box(Modifier.requiredSize(width = 1200.dp, height = 800.dp)) {
                    PlayScreen(state, PlayLayout.SinglePane, GameUiRegistry().apply { registerZipBoard() }, onBack = {}, onSolved = { _, _ -> })
                }
            }
        }
        composeRule.waitForIdle()

        val board = composeRule.onNodeWithTag("play.board").fetchSemanticsNode()
        val area = composeRule.onNodeWithTag("play.board_area").fetchSemanticsNode()
        with(density) {
            val capPx = PlayBoardMaxWidth.roundToPx()
            check(board.size.width == capPx) { "board not capped: ${board.size.width}px != $capPx" }
            check(board.size.height == capPx) { "grid aspect not exact: ${board.size.height}px != $capPx" }
        }
        val boardCenterX = board.positionInRoot.x + board.size.width / 2f
        val areaCenterX = area.positionInRoot.x + area.size.width / 2f
        check(abs(boardCenterX - areaCenterX) < 1f) { "board not centered: $boardCenterX vs $areaCenterX" }
    }

    /** A4: the cap is a max, not fixed — a phone pane keeps near-full width minus margins. */
    @Test
    fun `phone pane keeps near-full board width under standard margins`() {
        val state = bareState(daily())
        state.load()
        var density = Density(1f)
        composeRule.setContent {
            PuzzleBoxTheme {
                density = LocalDensity.current
                Box(Modifier.requiredSize(width = 411.dp, height = 900.dp)) {
                    PlayScreen(state, PlayLayout.SinglePane, GameUiRegistry().apply { registerZipBoard() }, onBack = {}, onSolved = { _, _ -> })
                }
            }
        }
        composeRule.waitForIdle()

        val board = composeRule.onNodeWithTag("play.board").fetchSemanticsNode()
        with(density) {
            val expected = (411.dp - Spacing.lg * 2).roundToPx()
            check(board.size.width == expected) { "board not near-full width: ${board.size.width}px != $expected" }
        }
    }

    @Test
    fun `registry renders the zip board through the play registration`() {
        val registry = GameUiRegistry()
        registry.registerZipBoard()
        val state = bareState(daily())
        state.load()
        composeRule.setContent {
            PuzzleBoxTheme {
                registry.Board(gameId = ZipDefinition.id, model = state.rendererModel())
            }
        }
        composeRule.onNodeWithTag("play.waypoint_dot_0").assertExists()
        composeRule.onNodeWithText("1").assertExists()
    }
}
