package com.ivandsky.puzzlebox.gameui.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where each part of two boards sits on either board, and how tall each board is, before any progress is applied. */
class ScrubItemsTest {
    private fun part(name: String) = BoardPart.Message(name, name)

    private fun items(from: List<String>, to: List<String>, height: Int = 10) =
        ScrubItems(from.map(::part), to.map(::part)).also { it.heights.fill(height) }

    private fun ScrubItems.indexOf(name: String) = all.indexOfFirst { it.part.key == "message:$name" }

    @Test
    fun `a part on both boards sits at its own place on each`() {
        val scrub = items(from = listOf("a", "b", "c"), to = listOf("c", "a", "b"))
        val heights = scrub.stack(gap = 2)

        assertEquals(ScrubHeights(28, 28), heights)
        assertTrue(scrub.all[scrub.indexOf("c")].both)
        assertEquals(18, scrub.fromY[scrub.indexOf("c")])
        assertEquals(-2, scrub.toY[scrub.indexOf("c")])
        assertEquals(-2, scrub.fromY[scrub.indexOf("a")])
        assertEquals(8, scrub.toY[scrub.indexOf("a")])
        assertEquals(-20, scrub.travel(scrub.indexOf("c")))
    }

    @Test
    fun `a part leaving stays where the board it leaves has it`() {
        val scrub = items(from = listOf("a", "b"), to = listOf("a"))
        scrub.stack(gap = 0)
        val b = scrub.indexOf("b")

        assertFalse(scrub.all[b].both)
        assertEquals(1, scrub.all[b].from)
        assertEquals(-1, scrub.all[b].to)
        assertEquals(10, scrub.fromY[b])
        assertEquals(10, scrub.toY[b])
    }

    @Test
    fun `a part arriving stays where the board it arrives on has it`() {
        val scrub = items(from = listOf("a"), to = listOf("a", "b"))
        scrub.stack(gap = 0)
        val b = scrub.indexOf("b")

        assertFalse(scrub.all[b].both)
        assertEquals(-1, scrub.all[b].from)
        assertEquals(1, scrub.all[b].to)
        assertEquals(10, scrub.fromY[b])
        assertEquals(10, scrub.toY[b])
    }

    @Test
    fun `boards of different lengths are as tall as their own parts`() {
        val scrub = items(from = listOf("a", "b"), to = listOf("a", "x", "y", "z"))
        val heights = scrub.stack(gap = 3)

        assertEquals(ScrubHeights(from = 17, to = 37), heights)
    }

    @Test
    fun `the first part pulls its own gap above it out of the height`() {
        val scrub = items(from = listOf("a"), to = listOf("a"))

        assertEquals(ScrubHeights(2, 2), scrub.stack(gap = 8))
        assertEquals(-8, scrub.fromY[0])
    }

    @Test
    fun `an empty board is no taller than nothing`() {
        val scrub = items(from = emptyList(), to = listOf("a"))

        assertEquals(ScrubHeights(from = 0, to = 6), scrub.stack(gap = 4))
    }

    @Test
    fun `parts keep each height when heights differ`() {
        val scrub = ScrubItems(listOf(part("a"), part("b")), listOf(part("b"), part("a")))
        scrub.heights[scrub.indexOf("a")] = 5
        scrub.heights[scrub.indexOf("b")] = 20
        scrub.stack(gap = 0)

        assertEquals(0, scrub.fromY[scrub.indexOf("a")])
        assertEquals(5, scrub.fromY[scrub.indexOf("b")])
        assertEquals(0, scrub.toY[scrub.indexOf("b")])
        assertEquals(20, scrub.toY[scrub.indexOf("a")])
    }

    @Test
    fun `a part on both boards is where its two places say at progress 0, half and 1`() {
        val scrub = items(from = listOf("a", "b"), to = listOf("b", "a"))
        scrub.stack(gap = 0)
        val a = scrub.indexOf("a")

        assertEquals(0f, scrub.offset(a, 0f), 0f)
        assertEquals(5f, scrub.offset(a, 0.5f), 0f)
        assertEquals(10f, scrub.offset(a, 1f), 0f)
    }

    @Test
    fun `a part that does not travel never moves`() {
        val scrub = items(from = listOf("a", "b"), to = listOf("a", "c"))
        scrub.stack(gap = 0)
        val a = scrub.indexOf("a")

        for (progress in listOf(0f, 0.5f, 1f)) assertEquals(0f, scrub.offset(a, progress), 0f)
        assertEquals(0f, scrub.depth(a), 0f)
    }

    @Test
    fun `the further a part goes the higher it is drawn and one on a single board is under all`() {
        val scrub = items(from = listOf("a", "b", "c", "d"), to = listOf("d", "b", "c", "e"))
        scrub.stack(gap = 0)

        assertEquals(31f, scrub.depth(scrub.indexOf("d")), 0f)
        assertEquals(0f, scrub.depth(scrub.indexOf("b")), 0f)
        assertEquals(-1f, scrub.depth(scrub.indexOf("a")), 0f)
        assertEquals(-1f, scrub.depth(scrub.indexOf("e")), 0f)
    }

    @Test
    fun `the list is the height of the board it is nearer to`() {
        val heights = ScrubHeights(from = 20, to = 60)

        assertEquals(20, heights.at(0f))
        assertEquals(40, heights.at(0.5f))
        assertEquals(60, heights.at(1f))
        assertEquals(30, ScrubHeights(30, 30).at(0.5f))
    }
}
