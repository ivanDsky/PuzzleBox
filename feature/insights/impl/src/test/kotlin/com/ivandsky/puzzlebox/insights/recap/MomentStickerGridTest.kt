package com.ivandsky.puzzlebox.insights.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentStickerGridTest {

    // Phone sheets as the card gives them: the golden phone's board (331 wide), wider phones, the reported split-screen
    // board, a short one.
    private val phone = listOf(331f, 363f).flatMap { w -> (260..460 step 40).map { w to it.toFloat() } } + listOf(353f to 302f, 330f to 250f)
    private val counts = listOf(1, 2, 4, 6, 14, 25, 32)

    private fun grid(n: Int, w: Float, h: Float) = stickerGrid(n, w, h, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)

    /** Whether [grid]'s [n] stickers, their insets and gaps, and their days fit a [w] by [h] board. */
    private fun fits(grid: StickerGrid, n: Int, w: Float, h: Float): Boolean {
        val rows = (n + grid.columns - 1) / grid.columns
        val across = grid.columns * grid.cellWidth + (grid.columns - 1) * grid.gapX + 2 * grid.side
        val label = if (grid.dated) LabelHeight else 0f
        val down = rows * (grid.size + label) + (rows - 1) * grid.gapY + grid.top + grid.side
        return across <= w + Slack && down <= h + Slack
    }

    // Catches the overflow on the report: stickers, their tilt and peel, and their days running past the board's edges.
    @Test
    fun `every sticker and its day stay inside a phone board`() {
        for ((w, h) in phone) for (n in counts) {
            val grid = grid(n, w, h)
            assertTrue("n=$n ${w}x$h $grid", fits(grid, n, w, h))
            assertTrue("n=$n ${w}x$h cell narrower than its day", !grid.dated || grid.cellWidth >= LabelWidth)
        }
    }

    // Catches stickers left small on a roomy board: no other column count would give a dense sheet's stickers more room.
    @Test
    fun `a dense sheet's stickers are as large as the board allows`() {
        for ((w, h) in phone) for (n in listOf(25, 32)) {
            val grid = grid(n, w, h)
            for (columns in 1..n) {
                val larger = grid.size * Larger
                val other = StickerGrid(columns, larger, maxOf(larger, if (grid.dated) LabelWidth else 0f), grid.dated)
                assertFalse("n=$n ${w}x$h $grid but $columns columns fit ${other.size}", fits(other, n, w, h))
            }
        }
        val golden = grid(32, 331f, 300f)
        assertEquals(6, golden.columns)
        assertEquals(40.1f, golden.size, 0.2f)
        assertEquals(41.6f, grid(32, 353f, 302f).size, 0.2f)
    }

    // Catches fixed gaps: wide on small stickers, cramped on large ones.
    @Test
    fun `the gaps scale with the stickers`() {
        for ((w, h) in phone) for (n in counts) {
            val grid = grid(n, w, h)
            assertTrue("n=$n ${w}x$h gapX ${grid.gapX}", grid.gapX in GapLeast * grid.size..GapMost * grid.size)
            if (!grid.dated) assertTrue("n=$n ${w}x$h gapY ${grid.gapY}", grid.gapY in GapLeast * grid.size..GapMost * grid.size)
        }
    }

    // Catches the insets shrinking to the bare padding: a peeled sticker's corner would touch the edge.
    @Test
    fun `the insets leave room for a tilted, peeled sticker`() {
        for ((w, h) in phone) for (n in counts) {
            val grid = grid(n, w, h)
            assertTrue("n=$n side ${grid.side}", grid.side >= Pad && grid.side - PeeledReach * grid.size >= EdgeClear)
            assertTrue("n=$n top ${grid.top}", grid.top >= grid.side + 5f)
        }
    }

    // Catches the fit breaking the reference layouts: 6 in 3 columns at 68dp, and 14 in 5 columns on a phone.
    @Test
    fun `a phone keeps the reference columns and caps`() {
        val six = grid(6, 331f, 420f)
        assertEquals(3, six.columns)
        assertEquals(68f, six.size, 0.01f)
        assertTrue(six.dated)
        assertEquals(120f, grid(1, 331f, 420f).size, 0.01f)
        for (w in listOf(331f, 363f)) for (h in listOf(380f, 420f, 460f)) {
            val fourteen = grid(14, w, h)
            assertEquals("${w}x$h", 5, fourteen.columns)
            assertTrue("${w}x$h", fourteen.dated)
        }
    }

    // Catches a year's stickers shrunk to specks under their days: a dense sheet drops the days.
    @Test
    fun `a year of stickers drops the days on a phone`() {
        for ((w, h) in phone.filter { it.second <= 420f }) assertFalse("${w}x$h", grid(32, w, h).dated)
    }

    // Catches the tablet ladder lost: a wide sheet grows (the card scrolls), so it takes the prototype's sizes and 7 columns.
    @Test
    fun `a wide sheet takes the tablet ladder`() {
        val wide = { n: Int -> stickerGrid(n, 788f, Float.POSITIVE_INFINITY, wide = true, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight) }
        assertEquals(176f, wide(1).size, 0.01f)
        assertEquals(128f, wide(6).size, 0.01f)
        assertEquals(3, wide(6).columns)
        for (n in listOf(14, 25, 32)) {
            assertEquals("n=$n", 7, wide(n).columns)
            assertEquals("n=$n", 92f, wide(n).size, 0.01f)
            assertTrue("n=$n", wide(n).dated)
        }
    }

    // Catches a divide by zero or a negative size when there is nothing to lay out or no room at all.
    @Test
    fun `no stickers or no room gives a sane grid`() {
        assertEquals(1, grid(0, 331f, 420f).columns)
        assertTrue(grid(6, 20f, 20f).size >= 0f)
    }
}

/** A day label ("Oct 25", "Sep 25") at labelMedium bold: about 40dp wide, 8dp gap plus a 16dp line. */
private const val LabelWidth = 40f
private const val LabelHeight = 24f

/** The sheet's padding, how far past its square a sticker tilted 9° and scaled 1.08 reaches, and the room left past that. */
private const val Pad = 12f
private const val PeeledReach = 0.118f
private const val EdgeClear = 6f

/** Gaps between 15% and 20% of a sticker; a sticker 2% larger than the chosen one must not fit at any column count. */
private const val GapLeast = 0.15f - 0.001f
private const val GapMost = 0.2f
private const val Larger = 1.02f
private const val Slack = 0.01f
