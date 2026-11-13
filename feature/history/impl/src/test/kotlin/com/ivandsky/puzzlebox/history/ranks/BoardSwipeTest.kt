package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.gestures.Orientation
import org.junit.Assert.assertEquals
import org.junit.Test

/** When a horizontal drag commits to a board step: a quarter of the width, or a fling (A34, SC 1); and which drags are the board's at all. */
class BoardSwipeTest {

    private val width = 1_000f
    private val fling = 700f
    private val slop = 8f

    @Test
    fun `a drag has no axis inside the touch slop, then its longer one`() {
        assertEquals(null, swipeAxis(7f, -7f, slop))
        assertEquals(Orientation.Horizontal, swipeAxis(-8f, 7f, slop))
        assertEquals(Orientation.Horizontal, swipeAxis(40f, -39f, slop))
        assertEquals(Orientation.Vertical, swipeAxis(7f, 8f, slop))
        assertEquals(Orientation.Vertical, swipeAxis(-39f, -40f, slop))
    }

    @Test
    fun `a drag as far along both axes is the page's scroll`() {
        assertEquals(Orientation.Vertical, swipeAxis(8f, 8f, slop))
    }

    @Test
    fun `a leftward drag past a quarter of the width goes forward, a rightward one back`() {
        assertEquals(1, swipeStep(-250f, 0f, width, fling))
        assertEquals(-1, swipeStep(250f, 0f, width, fling))
    }

    @Test
    fun `a short slow drag commits nothing`() {
        assertEquals(0, swipeStep(-249f, 100f, width, fling))
        assertEquals(0, swipeStep(0f, 0f, width, fling))
    }

    @Test
    fun `a fling commits however short the drag, by its own direction`() {
        assertEquals(1, swipeStep(-20f, -900f, width, fling))
        assertEquals(-1, swipeStep(20f, 900f, width, fling))
        assertEquals(1, swipeStep(60f, -700f, width, fling))
    }
}
