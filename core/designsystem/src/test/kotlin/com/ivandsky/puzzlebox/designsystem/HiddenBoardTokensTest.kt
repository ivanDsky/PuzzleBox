package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hidden board's look as the prototype fixed it (05, variant A): a change here is a design change. */
class HiddenBoardTokensTest {

    @Test
    fun `the blur is the prototype's 18, with its saturation, contrast and scale`() {
        assertEquals(18.dp, HiddenBoardTokens.Blur)
        assertEquals(0.6f, HiddenBoardTokens.Saturation)
        assertEquals(0.85f, HiddenBoardTokens.Contrast)
        assertEquals(1.18f, HiddenBoardTokens.Scale)
    }

    @Test
    fun `the veil is the surface at 28 percent and the slot is at most 400 dp high`() {
        assertEquals(0.28f, HiddenBoardTokens.VeilAlpha)
        assertEquals(400.dp, HiddenBoardTokens.MaxSide)
    }

    @Test
    fun `the pill is a comfortable 40 dp`() {
        assertEquals(40.dp, HiddenBoardTokens.PillMinHeight)
    }

    @Test
    fun `the scale pushes the blurred edge past the slot's clip`() {
        // The blur fades toward the board's edge over about its own width; scaled up, that edge sits outside the slot.
        val overhang = HiddenBoardTokens.MaxSide * (HiddenBoardTokens.Scale - 1f) / 2
        assertTrue("overhang $overhang", overhang >= HiddenBoardTokens.Blur)
    }
}
