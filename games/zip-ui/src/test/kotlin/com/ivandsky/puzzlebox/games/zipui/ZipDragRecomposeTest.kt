package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 12: an accepted cell redraws the path and the touched cells only, and a drag cut
 * short (pointer cancel, rotation) leaves the trace a valid committed prefix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipDragRecomposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rules = zipPlugin.rules as ZipRules
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var lastBytes: ByteArray? = null
    private var shown by mutableStateOf<ZipPlayController?>(null)
    private var cellComposed: (Int) -> Unit = {}

    private fun newController(): ZipPlayController =
        ZipPlayController(PlayEnvironment(scope, { lastBytes = it }, {}))

    private fun adopted(puzzle: ZipPuzzle = ZipPlayFixture.puzzle()): ZipPlayController = newController().also {
        check(it.adoptToday(ZipPlayFixture.envelope(puzzle), ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
    }

    private fun show(controller: ZipPlayController) {
        shown = controller
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalZipCellComposed provides { cellComposed(it) }) {
                    shown?.let {
                        ZipPlayScreen(
                            controller = it,
                            status = PlayBoardStatus.Ready,
                            layout = PlayLayout.SinglePane,
                            sidePanel = null,
                            onRetry = {},
                            onSolved = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun at(width: Int, height: Int, cell: Int) =
        Offset(width * (cell % 5 + 0.5f) / 5, height * (cell / 5 + 0.5f) / 5)

    /** Every committed step is a rule-valid extend from the start waypoint. */
    private fun assertValidTrace(controller: ZipPlayController, expected: List<Int>) {
        val session = controller.session!!
        assertEquals(expected, session.path.map { it.idx })
        var prefix = emptyList<Cell>()
        session.path.forEach { cell ->
            val verdict = rules.validateMove(ZipMove.Extend(cell), ZipState(session.puzzle, prefix))
            assertTrue("step to ${cell.idx} invalid: $verdict", verdict is ZipValidation.Valid)
            prefix = prefix + cell
        }
    }

    @Test
    fun `an accepted cell recomposes only that cell`() {
        show(adopted())
        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(at(width, height, 0))
            (1..2).forEach { moveTo(at(width, height, it)) }
        }
        composeRule.waitForIdle()

        val recomposed = mutableListOf<Int>()
        cellComposed = { recomposed += it }
        composeRule.onNodeWithTag("play.board").performTouchInput { moveTo(at(width, height, 3)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play.board").performTouchInput { up() }

        assertEquals("only the cell the path reached redraws", listOf(3), recomposed)
    }

    @Test
    fun `a cancelled pointer leaves the drawn cells as a valid trace`() {
        val controller = adopted()
        show(controller)
        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(at(width, height, 0))
            (1..3).forEach { moveTo(at(width, height, it)) }
            cancel()
        }
        composeRule.waitForIdle()
        assertValidTrace(controller, listOf(0, 1, 2, 3))

        // The cancelled stroke left nothing half-open: the next one continues from the tail.
        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(at(width, height, 3))
            moveTo(at(width, height, 4))
            up()
        }
        composeRule.waitForIdle()
        assertValidTrace(controller, listOf(0, 1, 2, 3, 4))
    }

    @Test
    fun `a fast shallow diagonal over an open board extends every crossed cell in order`() {
        // One move from cell 0 to cell 9, a row down over four columns: cells 2 and 7 are crossed
        // through their dead rims only, so cell 8 is caught two steps from the tail.
        val controller = adopted(ZipPlayFixture.puzzle().let { it.copy(board = it.board.copy(walls = emptySet())) })
        show(controller)
        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(at(width, height, 0))
            moveTo(at(width, height, 9))
            up()
        }
        composeRule.waitForIdle()
        assertValidTrace(controller, listOf(0, 1, 2, 7, 8, 9))
    }

    @Test
    fun `rotation mid-drag restores the committed trace and drawing continues`() {
        val first = adopted()
        show(first)
        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(at(width, height, 0))
            (1..3).forEach { moveTo(at(width, height, it)) }
        }
        composeRule.waitForIdle()

        // Recreation: a new controller restored from the last persisted bytes replaces the
        // screen while the finger is still down.
        val restored = newController()
        checkNotNull(restored.adoptRestored(checkNotNull(lastBytes)))
        composeRule.runOnIdle { shown = restored }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play.board").performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertValidTrace(restored, listOf(0, 1, 2, 3))

        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(at(width, height, 3))
            moveTo(at(width, height, 4))
            up()
        }
        composeRule.waitForIdle()
        assertValidTrace(restored, listOf(0, 1, 2, 3, 4))
    }
}
