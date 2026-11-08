package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoard
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live trace connector on the real board, mid-gesture (the finger stays down): every
 * letter the finger joins shows its connector in the rendered board. Frames are drawn every
 * 16 ms for a quarter second after each move, as a device would, so a line whose end never
 * advances to the last letter stays bare here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h700dp")
class StrandsTraceDragRenderTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show() = rule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            Box(Modifier.size(400.dp, 300.dp).testTag("host")) { StrandsBoard(controller) }
        }
    }

    /** The board pixel [colCells], [rowCells] cell units from its top-left, once frames have run. */
    private fun at(colCells: Float, rowCells: Float): Color {
        lateinit var image: ImageBitmap
        repeat(16) {
            rule.mainClock.advanceTimeBy(16)
            image = rule.onNodeWithTag("strands.board").captureToImage()
        }
        val pixels = image.toPixelMap()
        return pixels[(pixels.width * colCells / 4).toInt(), (pixels.height * rowCells / 4).toInt()]
    }

    /** The board pixel halfway between letter [from] and its neighbour [to] on the 4 × 4 board. */
    private fun between(from: Coord, to: Coord): Color =
        at((from.col + to.col) / 2f + 0.5f, (from.row + to.row) / 2f + 0.5f)

    private fun move(vararg to: Coord) = rule.onNodeWithTag("strands.board").performTouchInput {
        to.forEach { moveTo(Offset(width * (it.col + 0.5f) / 4, height * (it.row + 0.5f) / 4)) }
    }

    @Test
    fun `a held drag draws each connector it joins`() {
        show()
        rule.waitForIdle()
        val bare = between(Coord(1, 1), Coord(1, 2))

        rule.onNodeWithTag("strands.board").performTouchInput {
            down(Offset(width * 0.5f / 4, height * 1.5f / 4))
        }
        move(Coord(1, 1))
        val first = between(Coord(1, 0), Coord(1, 1))
        assertNotEquals(bare, first)

        move(Coord(1, 2))
        assertEquals(first, between(Coord(1, 1), Coord(1, 2)))
        move(Coord(1, 3))
        assertEquals(first, between(Coord(1, 2), Coord(1, 3)))
        assertEquals(first, between(Coord(1, 0), Coord(1, 1)))

        rule.onNodeWithTag("strands.board").performTouchInput { up() }
    }

    @Test
    fun `a held backtrack takes the last connector off`() {
        show()
        rule.waitForIdle()
        val bare = between(Coord(1, 2), Coord(1, 3))
        rule.onNodeWithTag("strands.board").performTouchInput {
            down(Offset(width * 0.5f / 4, height * 1.5f / 4))
        }
        move(Coord(1, 1), Coord(1, 2), Coord(1, 3))
        assertNotEquals(bare, between(Coord(1, 2), Coord(1, 3)))

        move(Coord(1, 2))
        assertEquals(bare, between(Coord(1, 2), Coord(1, 3)))

        rule.onNodeWithTag("strands.board").performTouchInput { up() }
    }

    @Test
    fun `a held finger between letters leans the connector along the free diagonal`() {
        show()
        rule.waitForIdle()
        val leanPoint = at(1.8f, 1.8f)
        val beyond = at(3.0f, 3.0f)

        rule.onNodeWithTag("strands.board").performTouchInput {
            down(Offset(width * 0.5f / 4, height * 1.5f / 4))
        }
        move(Coord(1, 1))
        // (1.8, 1.8) cell units: past the tail's dead zone, short of every join threshold,
        // so nothing commits — the end only leans toward the free diagonal (2, 2).
        rule.onNodeWithTag("strands.board").performTouchInput {
            moveTo(Offset(width * 1.8f / 4, height * 1.8f / 4))
        }
        assertNotEquals(leanPoint, at(1.8f, 1.8f))
        assertEquals("the lean reaches no further than the finger", beyond, at(3.0f, 3.0f))

        rule.onNodeWithTag("strands.board").performTouchInput { up() }
    }

    @Test
    fun `a released finger takes the lean off the board`() {
        show()
        rule.waitForIdle()
        val bare = at(1.8f, 1.8f)

        rule.onNodeWithTag("strands.board").performTouchInput {
            down(Offset(width * 0.5f / 4, height * 1.5f / 4))
        }
        move(Coord(1, 1))
        rule.onNodeWithTag("strands.board").performTouchInput {
            moveTo(Offset(width * 1.8f / 4, height * 1.8f / 4))
        }
        assertNotEquals(bare, at(1.8f, 1.8f))

        // Lift: the lean goes with the drag — nothing stays leaning past the last letter.
        rule.onNodeWithTag("strands.board").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(bare, at(1.8f, 1.8f))
    }

    @Test
    fun `a held drag onto an earlier letter keeps the line - the path is a wall`() {
        show()
        rule.waitForIdle()

        rule.onNodeWithTag("strands.board").performTouchInput {
            down(Offset(width * 0.5f / 4, height * 1.5f / 4))
        }
        move(Coord(1, 1), Coord(1, 2), Coord(2, 2), Coord(2, 1))
        val joined = between(Coord(2, 2), Coord(2, 1))
        assertNotEquals(at(1.5f, 2.0f), joined)

        // Back up over (1, 1): traced earlier, not the previous letter — the wall refuses,
        // the U-turn stays, the connector never cuts back to it and nothing leans at it.
        move(Coord(1, 1))
        assertEquals(joined, between(Coord(2, 2), Coord(2, 1)))
        assertEquals("no lean into the traced letter", at(1.5f, 2.0f), between(Coord(2, 1), Coord(1, 1)))

        rule.onNodeWithTag("strands.board").performTouchInput { up() }
    }
}
