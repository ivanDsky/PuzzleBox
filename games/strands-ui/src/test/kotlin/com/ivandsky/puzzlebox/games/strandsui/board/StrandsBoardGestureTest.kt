package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoard
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board's own input on the real controller over the fixture board (A12): a drag spells
 * a word, taps spell and submit one, a diagonal through a corner skips the side letters,
 * TalkBack's double-tap is a tap (SC12), a touch beside the board clears the trace, and
 * each outcome buzzes. A 400 × 300 dp host gives a 64 dp cell and gutters on both sides.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h700dp")
class StrandsBoardGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private val buzzes = mutableListOf<HapticFeedbackType>()

    private val controller = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show() = rule.setContent {
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                buzzes += hapticFeedbackType
            }
        }
        PuzzleBoxTheme(darkTheme = false) {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                Box(Modifier.size(400.dp, 300.dp).testTag("host")) { StrandsBoard(controller) }
            }
        }
    }

    private fun board(): SemanticsNodeInteraction = rule.onNodeWithTag("strands.board")

    private fun cell(at: Coord) = rule.onNodeWithTag("strands.cell.${at.row}.${at.col}")

    /** The centre of [at] on the 4 × 4 board, in the board node's own coordinates. */
    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / 4, height * (at.row + 0.5f) / 4)

    private fun stateOf(at: Coord) = controller.model.cells[at.row * controller.model.cols + at.col].state

    @Test
    fun `dragging across the spangram row finds it`() {
        show()
        board().performTouchInput {
            down(centreOf(Coord(1, 0)))
            (1 until 4).forEach { moveTo(centreOf(Coord(1, it))) }
            up()
        }
        rule.waitForIdle()

        StrandsUiFixtures.row(1).forEach { assertEquals(CellState.FoundSpangram, stateOf(it)) }
        // A tick per letter joined, the drag's first included, then the spangram's buzz.
        assertEquals(List(4) { HapticFeedbackType.TextHandleMove } + HapticFeedbackType.LongPress, buzzes)
    }

    @Test
    fun `tapping each letter, then the last again, submits the word`() {
        show()
        StrandsUiFixtures.row(0).forEach { cell(it).performClick() }
        assertEquals(listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2), Coord(0, 3)), controller.trace.path)
        cell(Coord(0, 3)).assert(hasContentDescription("S, selected"))

        cell(Coord(0, 3)).performClick()
        rule.waitForIdle()

        StrandsUiFixtures.row(0).forEach { assertEquals(CellState.FoundTheme, stateOf(it)) }
        cell(Coord(0, 3)).assert(hasContentDescription("S, theme"))
        assertEquals(HapticFeedbackType.Confirm, buzzes.last())
    }

    @Test
    fun `a diagonal drag through the corner skips the side letters`() {
        show()
        board().performTouchInput {
            val a = centreOf(Coord(0, 0))
            val b = centreOf(Coord(1, 1))
            down(a)
            // A finger never runs true: cut the corner through the right neighbour's own
            // corner, deep inside (0, 1)'s square but outside its inner circle.
            val corner = Offset(width * 0.3f, height * 0.2f)
            (1..10).forEach { moveTo(a + (corner - a) * (it / 10f)) }
            (1..10).forEach { moveTo(corner + (b - corner) * (it / 10f)) }
        }
        assertEquals(listOf(Coord(0, 0), Coord(1, 1)), controller.trace.path)
        board().performTouchInput { up() }
    }

    @Test
    fun `a move that never reaches another letter is a tap`() {
        show()
        board().performTouchInput {
            val a = centreOf(Coord(2, 2))
            down(a)
            moveTo(a + Offset(width / 16f, 0f))
            up()
        }
        assertEquals(listOf(Coord(2, 2)), controller.trace.path)
    }

    @Test
    fun `TalkBack's double-tap is a tap`() {
        show()
        cell(Coord(3, 0)).assert(hasContentDescription("C"))
        StrandsUiFixtures.row(3).forEach { cell(it).performSemanticsAction(SemanticsActions.OnClick) }
        cell(Coord(3, 3)).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        StrandsUiFixtures.row(3).forEach { assertEquals(CellState.FoundTheme, stateOf(it)) }
    }

    @Test
    fun `a touch beside the board clears the trace`() {
        show()
        cell(Coord(0, 0)).performClick()
        assertEquals(1, controller.trace.path.size)

        rule.onNodeWithTag("host").performTouchInput { click(Offset(10f, height / 2f)) }

        assertEquals(emptyList<Coord>(), controller.trace.path)
    }

    @Test
    fun `a word not on the board is rejected with its own buzz`() {
        show()
        // P, I, then K below-right of I: adjacent, but PIK is no word.
        listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2)).forEach { cell(it).performClick() }
        cell(Coord(1, 2)).performClick()

        assertEquals(HapticFeedbackType.Reject, buzzes.last())
        assertEquals(emptyList<Coord>(), controller.trace.path)
    }
}
