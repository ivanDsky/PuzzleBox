package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The compare is side by side at expanded width in either orientation, given room for two boards. */
class CompareLayoutPolicyTest {
    private fun at(w: Int, h: Int) = compareLayoutFor(w.dp, h.dp)

    @Test
    fun `phone landscape stacks the boards`() {
        assertEquals(CompareLayout.FlipPeek, at(891, 448))
    }

    @Test
    fun `tablet portrait is side by side`() {
        assertEquals(CompareLayout.Twin, at(840, 1280))
    }

    @Test
    fun `tablet landscape is side by side`() {
        assertEquals(CompareLayout.Twin, at(1280, 800))
    }

    @Test
    fun `narrower than expanded flips at any height`() {
        assertEquals(CompareLayout.FlipPeek, at(839, 1280))
        assertEquals(CompareLayout.FlipPeek, at(411, 800))
    }
}
