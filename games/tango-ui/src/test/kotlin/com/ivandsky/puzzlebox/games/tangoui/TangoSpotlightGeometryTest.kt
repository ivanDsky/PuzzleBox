package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoSpotlightGlyphs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the overlay mount (00 §3): the Spotlight's bounds are the cell grid's, so a lit cell's rect, cut uniformly out
 * of the overlay's bounds as the overlay draws it, is the rect the board gives that cell, at a phone width and when the
 * pane is shorter than it is wide (the square board then leaves room beside it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoSpotlightGeometryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = TangoRendererModel.of(
        TangoState(TangoPlayFixture.puzzle, List(SIZE * SIZE) { TangoPlayFixture.puzzle.givens[it] }),
        hint = null,
    )

    private val lit = Spotlight(
        SIZE,
        SIZE,
        mapOf(LIT to SpotlightRole.TARGET),
        callout = null,
        TangoSpotlightGlyphs(SIZE, mapOf(LIT to TangoCell.MOON)),
        playId = 1,
    )

    private fun show(modifier: Modifier) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoBoardRenderer(spotlight = { lit }).Content(model, modifier)
            }
        }
        composeRule.waitForIdle()
    }

    private fun overlay(): Rect = composeRule.onNodeWithTag(SPOTLIGHT_TAG).fetchSemanticsNode().boundsInRoot

    private fun cell(idx: Int): Rect = composeRule
        .onNode(hasContentDescription(TangoSemantics.cellContentDescription(idx / SIZE, idx % SIZE), substring = true))
        .fetchSemanticsNode()
        .boundsInRoot

    /** The rect the overlay draws [idx] in: its bounds cut into equal rows and columns. */
    private fun overlayCell(idx: Int): Rect {
        val o = overlay()
        val w = o.width / SIZE
        val h = o.height / SIZE
        val col = idx % SIZE
        val row = idx / SIZE
        return Rect(o.left + col * w, o.top + row * h, o.left + (col + 1) * w, o.top + (row + 1) * h)
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        val message = "expected $expected, was $actual"
        assertEquals(message, expected.left, actual.left, PX_TOLERANCE)
        assertEquals(message, expected.top, actual.top, PX_TOLERANCE)
        assertEquals(message, expected.right, actual.right, PX_TOLERANCE)
        assertEquals(message, expected.bottom, actual.bottom, PX_TOLERANCE)
    }

    @Test
    fun `a lit cell's rect on the 6x6 board at a phone width is the board's own rect for that cell`() {
        show(Modifier.fillMaxWidth())

        assertRect(cell(LIT), overlayCell(LIT))
    }

    @Test
    fun `the overlay covers the cell grid exactly, frame included`() {
        show(Modifier.fillMaxWidth())

        assertRect(Rect(cell(0).topLeft, cell(SIZE * SIZE - 1).bottomRight), overlay())
    }

    @Test
    fun `in a pane shorter than it is wide the overlay stays on the square board`() {
        show(Modifier.size(width = 360.dp, height = 200.dp))

        assertRect(Rect(cell(0).topLeft, cell(SIZE * SIZE - 1).bottomRight), overlay())
        assertRect(cell(LIT), overlayCell(LIT))
    }

    private companion object {
        const val SIZE = 6

        /** Bottom right: the farthest cell from the board's origin, where an offset or a scale error is largest. */
        const val LIT = 35
        const val SPOTLIGHT_TAG = "tango.spotlight"

        /** The board's weighted rows and columns round each cell to whole pixels. */
        const val PX_TOLERANCE = 1f
    }
}
