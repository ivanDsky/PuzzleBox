package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.MoveHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrandsMoveHapticTest {

    private fun snap(
        letters: Int = 0,
        found: Int = 0,
        hints: Int = 0,
        rejections: Int = 0,
        solved: Boolean = false,
    ) = StrandsMoveSnapshot(
        trace = Trace(List(letters) { Coord(0, it) }),
        found = found,
        hintsUsed = hints,
        rejections = rejections,
        solved = solved,
    )

    private fun classify(before: StrandsMoveSnapshot, after: StrandsMoveSnapshot) = strandsMoveHaptic(before, after)

    @Test
    fun `a letter added during a drag is a placed tick`() {
        assertEquals(MoveHaptic.Placed, classify(snap(letters = 1), snap(letters = 2)))
    }

    @Test
    fun `a found theme word is a placed tick`() {
        assertEquals(MoveHaptic.Placed, classify(snap(letters = 4), snap(found = 1)))
    }

    @Test
    fun `a found spangram is a placed tick`() {
        assertEquals(MoveHaptic.Placed, classify(snap(letters = 5), snap(found = 1)))
    }

    @Test
    fun `the solving move is null, the solve confirm owns it`() {
        assertNull(classify(snap(letters = 4, found = 3), snap(found = 4, solved = true)))
    }

    @Test
    fun `a rejected word is a rejected buzz`() {
        assertEquals(MoveHaptic.Rejected, classify(snap(letters = 3), snap(rejections = 1)))
    }

    @Test
    fun `a second rejection of the same kind still buzzes`() {
        assertEquals(MoveHaptic.Rejected, classify(snap(rejections = 1), snap(rejections = 2)))
    }

    @Test
    fun `a hint is null, even when it turns up a word`() {
        assertNull(classify(snap(), snap(hints = 1, found = 1)))
    }

    @Test
    fun `a valid non-theme word is null`() {
        assertNull(classify(snap(letters = 4), snap()))
    }

    @Test
    fun `a letter removed is null`() {
        assertNull(classify(snap(letters = 2), snap(letters = 1)))
    }

    @Test
    fun `no change is null`() {
        assertNull(classify(snap(letters = 2, found = 1), snap(letters = 2, found = 1)))
    }
}
