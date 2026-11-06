package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.degradeBannerMessage
import com.ivandsky.puzzlebox.gameui.unavailableMessage
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
 * Robolectric compose coverage for the Zip play screen (A17): states, chrome, and the
 * ticket-23 solved handoff (the win moment itself lives on the result route now).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipPlayUiScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The (timeMs, hintsUsed) handed to the result route, captured per test. */
    private var solvedFacts: Pair<Long, Int>? = null

    private val day: LocalDate = LocalDate.of(2026, 10, 31)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun bareController(): ZipPlayController =
        ZipPlayController(PlayEnvironment(scope, {}, {}))

    private fun adoptedController(): ZipPlayController = bareController().also {
        check(it.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(day)) == Adopt.Adopted)
    }

    private fun readyController(
        layout: PlayLayout = PlayLayout.SinglePane,
        banner: String? = null,
    ): ZipPlayController = adoptedController().also {
        setContent(it, PlayBoardStatus.Ready(banner), layout)
    }

    private fun setContent(
        controller: ZipPlayController,
        status: PlayBoardStatus,
        layout: PlayLayout = PlayLayout.SinglePane,
        onRetry: () -> Unit = {},
    ) {
        solvedFacts = null
        composeRule.setContent {
            PuzzleBoxTheme {
                ZipPlayScreen(
                    controller = controller,
                    status = status,
                    layout = layout,
                    onRetry = onRetry,
                    onSolved = { timeMs, hintsUsed -> solvedFacts = timeMs to hintsUsed },
                )
            }
        }
    }

    private fun solve(controller: ZipPlayController) = composeRule.runOnIdle {
        ZipPlayFixture.solution.forEach { cell ->
            controller.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
        }
    }

    /** The solve moment's delays don't hold the rule busy: let the solve reach the board,
     *  then run the clock past the moment. */
    private fun playSolveMoment() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    @Test
    fun `loading state renders the board loading indicator`() {
        setContent(bareController(), PlayBoardStatus.Loading)
        composeRule.onNodeWithTag("play.loading").assertExists()
    }

    // The default 320×470 Robolectric window leaves the error pane ~290dp under the play
    // chrome — the retry button is clipped to zero height there. A phone-sized window.
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @Test
    fun `unavailable state renders the error with a working retry`() {
        var retries = 0
        setContent(
            bareController(),
            PlayBoardStatus.Unavailable(unavailableMessage(FetchError.Unavailable("no net"))),
            onRetry = { retries++ },
        )
        composeRule.onNodeWithTag("play.error").assertExists()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { check(retries == 1) { "retry did not reach onRetry" } }
    }

    @Test
    fun `ready board renders the play chrome`() {
        readyController()
        composeRule.onNodeWithTag("play.board").assertExists()
        composeRule.onNodeWithTag("play.side_panel").assertExists()
        composeRule.onNodeWithTag("play.action_bar").assertExists()
        composeRule.onNodeWithTag("play.hint").assertExists()
        // Zip has a Clear: the trace empties to the start state.
        composeRule.onNodeWithTag("play.clear").assertExists()
        // Ticket 22: the start waypoint is a dot with its bold label INSIDE it — bare
        // corner numbers are gone.
        composeRule.onNodeWithTag("play.waypoint_dot_0").assertExists()
        composeRule.onNodeWithText("1").assertExists()
        composeRule.onNodeWithText("Solved!").assertDoesNotExist()
    }

    @Test
    fun `degraded ready shows the banner but stays playable`() {
        readyController(banner = degradeBannerMessage(FetchError.Unavailable("offline")))
        composeRule.onNodeWithTag("play.degrade_banner").assertExists()
        composeRule.onNodeWithText("You're offline. Playing today's saved puzzle.").assertExists()
        composeRule.onNodeWithTag("play.board").assertExists()
    }

    @Test
    fun `hint button counts up and highlights a cell`() {
        val controller = readyController()
        composeRule.onNodeWithTag("play.hint").performClick()
        composeRule.runOnIdle { check(controller.session?.hintsUsed == 1) { "hint not counted" } }
        composeRule.onNodeWithTag("play.hint").assert(hasText("1", substring = true))
        composeRule.onNodeWithTag("play.hint_cell").assertExists()
    }

    @Test
    fun `hint autoplace shows the arrow at the new head`() {
        val controller = readyController()
        composeRule.runOnIdle { controller.onDragEvent(DragEvent.Extend(GridCell(0, 0))) }
        // Manual clock: apply one frame for the placed arrow, then run out its ~2s hold
        // + fade and confirm it clears. Representation: the plain centered arrowhead
        // glyph (ticket 28 r5) under the play.hint_arrow tag.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("play.hint").performClick()
        composeRule.runOnIdle { check(controller.hintArrow != null) { "hint did not place an arrow" } }
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.onNodeWithTag("play.hint_arrow").assertExists()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.onNodeWithTag("play.hint_arrow").assertDoesNotExist()
    }

    @Test
    fun `solving hands the frozen facts to the result route once`() {
        val controller = readyController()
        solve(controller)
        playSolveMoment()
        // Ticket 23: the win moment is a route handoff, not an overlay — the screen
        // carries the frozen A3 facts (time, hints) and never renders win content.
        val facts = solvedFacts
        check(facts != null) { "solving did not hand off to the result route" }
        check(facts.first >= 0) { "negative time handed off" }
        check(facts.second == 0) { "no hints were used on this solve" }
        composeRule.onNodeWithText("Solved!").assertDoesNotExist()
        composeRule.onNodeWithTag("play.board").assertExists()
    }

    /** A14: the solve moment plays first (sweep, then the glyph stamp), then hands off once. */
    @Test
    fun `solving plays the solve moment before the hand off`() {
        val controller = readyController()
        solve(controller)
        // Idle doesn't wait out the moment's delays: the board is solved, the hand-off pending.
        composeRule.waitForIdle()
        check(solvedFacts == null) { "handed off before the solve moment played" }

        playSolveMoment()
        composeRule.onNodeWithTag("play.solve_stamp").assertExists()
        check(solvedFacts != null) { "the solve moment never handed off" }
    }

    /** A restored, already-solved board has no moment to play: it hands off at once. */
    @Test
    fun `an already solved board hands off at once`() {
        val controller = adoptedController()
        ZipPlayFixture.solution.forEach { cell ->
            controller.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
        }
        check(controller.isComplete)
        composeRule.mainClock.autoAdvance = false
        setContent(controller, PlayBoardStatus.Ready(null))
        composeRule.mainClock.advanceTimeBy(16)
        check(solvedFacts != null) { "a solved board waited for a moment it never plays" }
    }

    @Test
    fun `a fresh entry does not hand off to the result route`() {
        readyController()
        composeRule.waitForIdle()
        check(solvedFacts == null) { "unsolved session handed off to the result route" }
    }

    @Test
    fun `replay resets to a fresh board and can hand off again`() {
        val controller = readyController()
        solve(controller)
        playSolveMoment()
        check(solvedFacts != null) { "first solve did not hand off" }
        composeRule.runOnIdle { controller.replay() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { check(controller.displayedElapsedMs() == 0L) { "replay kept the old time" } }
        composeRule.runOnIdle { check(controller.session?.path?.isEmpty() == true) }

        solve(controller)
        playSolveMoment()
        check(solvedFacts != null) { "second solve did not hand off" }
    }

    @Test
    fun `two pane arrangement renders board and side panel`() {
        readyController(layout = PlayLayout.TwoPane)
        composeRule.onNodeWithTag("play.board").assertExists()
        composeRule.onNodeWithTag("play.side_panel").assertExists()
    }

    /** A4: the board is capped at [PlayBoardMaxWidth], centered in its pane, aspect exact. */
    @Test
    fun `wide pane caps the board at the max width and centers it`() {
        val controller = adoptedController()
        var density = Density(1f)
        composeRule.setContent {
            PuzzleBoxTheme {
                density = LocalDensity.current
                Box(Modifier.requiredSize(width = 1200.dp, height = 800.dp)) {
                    ZipPlayScreen(controller, PlayBoardStatus.Ready(null), PlayLayout.SinglePane, onRetry = {}, onSolved = { _, _ -> })
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
        val controller = adoptedController()
        var density = Density(1f)
        composeRule.setContent {
            PuzzleBoxTheme {
                density = LocalDensity.current
                Box(Modifier.requiredSize(width = 411.dp, height = 900.dp)) {
                    ZipPlayScreen(controller, PlayBoardStatus.Ready(null), PlayLayout.SinglePane, onRetry = {}, onSolved = { _, _ -> })
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
}
