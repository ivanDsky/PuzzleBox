package com.ivandsky.puzzlebox.gameui.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the place number rolls through, and when a podium medal flips in or drains out. */
class RankStripTest {

    @Test
    fun `a short move lists every place between, in the order it passes them going down`() {
        val strip = rankStrip(from = 3, to = 6)

        assertEquals(listOf(3, 4, 5, 6), strip.cells)
        assertEquals(3, strip.cells[strip.start])
        assertEquals(6, strip.cells[strip.end])
    }

    @Test
    fun `a move of seven places still lists each one`() {
        assertEquals((1..8).toList(), rankStrip(from = 1, to = 8).cells)
        assertEquals((1..8).toList(), rankStrip(from = 8, to = 1).cells)
    }

    @Test
    fun `a longer move gives eight evenly spaced places from the old to the new`() {
        val down = rankStrip(from = 1, to = 15)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), down.cells)

        val up = rankStrip(from = 20, to = 6)
        assertEquals(8, up.cells.size)
        assertEquals(20, up.cells[up.start])
        assertEquals(6, up.cells[up.end])
        val steps = up.cells.zipWithNext { a, b -> b - a }
        assertTrue("steps $steps are even to within one place", steps.max() - steps.min() <= 1)
    }

    @Test
    fun `an unchanged place is one cell that does not roll`() {
        val strip = rankStrip(from = 4, to = 4)

        assertEquals(listOf(4), strip.cells)
        assertEquals(strip.start, strip.end)
        assertEquals(false, strip.rolls)
    }

    @Test
    fun `moving down the board scrolls toward the later cells and moving up toward the earlier ones`() {
        val down = rankStrip(from = 2, to = 5)
        assertTrue("down starts at the old place and scrolls on", down.start < down.end)
        assertEquals(2, down.cells[down.start])

        val up = rankStrip(from = 5, to = 2)
        assertTrue("up starts at the old place, which is last, and scrolls back", up.start > up.end)
        assertEquals(5, up.cells[up.start])
        assertEquals(2, up.cells[up.end])
        assertTrue(down.rolls && up.rolls)
    }

    @Test
    fun `the cell at the centre is the nearest one, so a roll can be retargeted from where it is`() {
        val strip = rankStrip(from = 2, to = 6)

        assertEquals(2, strip.centre(0.2f))
        assertEquals(4, strip.centre(1.6f))
        assertEquals(6, strip.centre(9f))
    }

    @Test
    fun `entering the podium flips the medal in and leaving it drains it out`() {
        assertEquals(MedalChange.FlipIn, medalChange(from = 4, to = 3))
        assertEquals(MedalChange.FlipIn, medalChange(from = 9, to = 1))
        assertEquals(MedalChange.Drain, medalChange(from = 3, to = 4))
        assertEquals(MedalChange.Drain, medalChange(from = 1, to = 12))
    }

    @Test
    fun `a move inside the podium, or outside it, neither flips nor drains`() {
        assertEquals(MedalChange.None, medalChange(from = 2, to = 1))
        assertEquals(MedalChange.None, medalChange(from = 1, to = 3))
        assertEquals(MedalChange.None, medalChange(from = 5, to = 9))
        assertEquals(MedalChange.None, medalChange(from = 3, to = 3))
    }

    @Test
    fun `the first sight of a place never flips, but a row that arrives onto the podium does`() {
        assertEquals(MedalChange.None, medalChange(from = null, to = 1))
        assertEquals(MedalChange.None, medalChange(from = null, to = 7, arrival = true))
        assertEquals(MedalChange.FlipIn, medalChange(from = null, to = 2, arrival = true))
    }
}
