package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoard
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board's own input on the real controller over the fixture board (A12): a drag spells
 * a word, taps spell and submit one, a diagonal through a corner skips the side letters,
 * TalkBack's double-tap is a tap and each letter is one stop (SC12), a touch beside the board
 * clears the trace, each outcome buzzes, and a found word settles once: a board composed
 * afresh rests (A33). A 400 × 300 dp host gives a 75 dp cell and gutters on both sides.
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

    /** Unmount and remount the board, as rotation or re-entry does. */
    private var mounted by mutableStateOf(true)

    private fun show() = rule.setContent {
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                buzzes += hapticFeedbackType
            }
        }
        PuzzleBoxTheme(darkTheme = false) {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                Box(Modifier.size(400.dp, 300.dp).testTag("host")) { if (mounted) StrandsBoard(controller) }
            }
        }
    }

    private fun board(): SemanticsNodeInteraction = rule.onNodeWithTag("strands.board")

    private fun cell(at: Coord) = rule.onNodeWithTag("strands.cell.${at.row}.${at.col}")

    /** The centre of [at] on the 4 × 4 board, in the board node's own coordinates. */
    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / 4, height * (at.row + 0.5f) / 4)

    private fun pixels(at: Coord): IntArray = cell(at).captureToImage().toPixelMap().buffer

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
    fun `a found letter is a tile on its lip, the lip showing below the face`() {
        show()
        StrandsUiFixtures.row(0).forEach { cell(it).performClick() }
        cell(Coord(0, 3)).performClick()
        rule.waitForIdle()

        val image = cell(Coord(0, 0)).captureToImage().toPixelMap()
        val token = image.height * StrandsBoardSpec.TokenFraction
        val lip = token * StrandsBoardSpec.TokenLipFraction
        val faceBottom = image.height / 2f + token / 2
        assertEquals(LightBoardTokens.strandsThemeLip, image[image.width / 2, (faceBottom + lip / 2).toInt()])
        assertEquals(LightBoardTokens.strandsThemeFill, image[image.width / 2, (faceBottom - lip).toInt()])
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
    fun `a wobbly diagonal drawn beside the centre line takes only the diagonal letters`() {
        show()
        board().performTouchInput {
            val pitch = width / 4f
            // 0.25 pitch off the centre line, swinging ±20° about the diagonal's heading.
            val beside = Offset(0.25f, -0.25f) * (pitch / sqrt(2f))
            val start = centreOf(Coord(0, 0)) + beside
            down(start)
            var at = start
            // Sweep on until the finger's progress along the diagonal reaches the far letter's centre.
            val far = centreOf(Coord(3, 3))
            var i = 0
            while (at.x + at.y < far.x + far.y) {
                val heading = Math.toRadians(45.0 + 20.0 * sin(i++ * 0.7))
                at += Offset(cos(heading).toFloat(), sin(heading).toFloat()) * (pitch * 0.05f)
                moveTo(at)
            }
        }
        assertEquals(listOf(Coord(0, 0), Coord(1, 1), Coord(2, 2), Coord(3, 3)), controller.trace.path)
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

    @Test
    fun `each letter is one TalkBack stop that carries its state`() {
        show()
        // The tree TalkBack walks: the drawn letter is no stop of its own.
        rule.onAllNodesWithText("C").assertCountEquals(0)
        cell(Coord(3, 0)).onChildren().assertCountEquals(0)
        cell(Coord(3, 0)).assert(hasContentDescription("C"))
    }

    @Test
    fun `a found word settles once, and a board composed afresh rests`() {
        show()
        val first = Coord(0, 0)
        rule.mainClock.autoAdvance = false
        StrandsUiFixtures.row(0).forEach(controller::tap)
        controller.tap(StrandsUiFixtures.row(0).last())
        rule.mainClock.advanceTimeByFrame()
        val settling = pixels(first)
        rule.mainClock.advanceTimeBy(SETTLED_MS)
        val rest = pixels(first)
        // Taking the event does not cut the settle short: its first frame is still the trace colour.
        assertFalse(settling.contentEquals(rest))
        assertNull(controller.lastFound)

        mounted = false
        rule.mainClock.advanceTimeByFrame()
        mounted = true
        rule.mainClock.advanceTimeByFrame()
        assertTrue(pixels(first).contentEquals(rest))
    }

    private companion object {
        /** Past the whole four-letter settle and its stagger. */
        const val SETTLED_MS = 3_000L
    }
}
