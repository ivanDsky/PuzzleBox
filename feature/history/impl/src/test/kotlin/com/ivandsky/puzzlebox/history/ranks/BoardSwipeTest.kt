package com.ivandsky.puzzlebox.history.ranks

import org.junit.Assert.assertEquals
import org.junit.Test

/** When a horizontal drag commits to a board step: a quarter of the width, or a fling (A34, SC 1). */
class BoardSwipeTest {

    private val width = 1_000f
    private val fling = 700f

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
