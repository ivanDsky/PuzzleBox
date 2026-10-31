package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.data.repository.ZipToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.Board
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric compose coverage for the play screen (A17): states, chrome, win overlay. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.setContent {
            PuzzleBoxTheme {
                PlayScreen(
                    state = state,
                    layout = layout,
                    registry = GameUiRegistry().apply { registerZipBoard() },
                    onBack = {},
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
        composeRule.setContent {
            PuzzleBoxTheme {
                PlayScreen(state, layout, registry, onBack = {})
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
        // The start waypoint's number label is on the board.
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
    fun `win overlay celebrates with the record badge after the write`() {
        val state = readyState()
        composeRule.runOnIdle {
            PlayTestSupport.solution.forEach { cell ->
                state.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
            }
        }
        composeRule.onNodeWithText("Solved!").assertExists()
        composeRule.onNodeWithText("New personal record").assertExists()
        composeRule.onNodeWithText("Play again").assertExists()
    }

    @Test
    fun `replay resets to a fresh board`() {
        val state = readyState()
        composeRule.runOnIdle {
            PlayTestSupport.solution.forEach { cell ->
                state.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
            }
        }
        composeRule.onNodeWithText("Play again").performClick()
        composeRule.onNodeWithText("Solved!").assertDoesNotExist()
        composeRule.onNodeWithText("00:00").assertExists()
        composeRule.runOnIdle { check(state.session?.path?.isEmpty() == true) }
    }

    @Test
    fun `two pane arrangement renders board and side panel`() {
        readyState(layout = PlayLayout.TwoPane)
        composeRule.onNodeWithTag("play.board").assertExists()
        composeRule.onNodeWithTag("play.side_panel").assertExists()
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
        composeRule.onNodeWithText("1").assertExists()
    }
}
