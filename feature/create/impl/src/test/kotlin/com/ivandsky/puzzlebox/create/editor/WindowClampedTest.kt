package com.ivandsky.puzzlebox.create.editor

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** The verdict card popover at a 320 dp window (1 px = 1 dp here): never past either edge. */
class WindowClampedTest {

    private val window = IntSize(320, 640)

    /** The pill near the bar's end, left of the overflow. */
    private val pill = IntRect(left = 170, top = 8, right = 270, bottom = 56)

    @Test
    fun `a card wider than the room left of the pill's end moves right to the margin`() {
        val position = WindowClamped.overTopEnd(margin = 16).calculatePosition(pill, window, LayoutDirection.Ltr, IntSize(288, 400))

        assertEquals(IntOffset(16, 16), position)
    }

    @Test
    fun `a card that fits keeps its end on the pill's end`() {
        val position = WindowClamped.overTopEnd(margin = 16).calculatePosition(pill, window, LayoutDirection.Ltr, IntSize(200, 400))

        assertEquals(IntOffset(70, 16), position)
    }

    @Test
    fun `right to left, the card starts at the pill's left and stays inside the right margin`() {
        val position = WindowClamped.overTopEnd(margin = 16).calculatePosition(pill, window, LayoutDirection.Rtl, IntSize(200, 400))

        assertEquals(IntOffset(104, 16), position)
    }

    @Test
    fun `above the anchor, centred, clamped to the window`() {
        val strip = IntRect(left = 0, top = 500, right = 100, bottom = 560)

        val position = WindowClamped.above(gap = 8).calculatePosition(strip, window, LayoutDirection.Ltr, IntSize(200, 100))

        assertEquals(IntOffset(0, 392), position)
    }
}
