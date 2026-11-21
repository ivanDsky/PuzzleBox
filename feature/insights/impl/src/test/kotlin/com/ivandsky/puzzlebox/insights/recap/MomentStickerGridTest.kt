package com.ivandsky.puzzlebox.insights.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentStickerGridTest {

    // Phone sheets as the card gives them: the golden phone's board (331 wide), wider phones, the reported split-screen
    // board, a short one.
    private val phone = listOf(331f, 363f).flatMap { w -> (260..460 step 40).map { w to it.toFloat() } } + listOf(353f to 302f, 330f to 250f)

    // Tablet sheets: the golden tablet's year and default boards (772 by 265 and 283), the reference's, taller ones.
    private val tablet = listOf(772f to 265f, 772f to 283f, 788f to 326f, 772f to 460f, 720f to 640f)
    private val boards = phone.map { Board(it.first, it.second, wide = false) } + tablet.map { Board(it.first, it.second, wide = true) }
    private val counts = listOf(1, 2, 4, 6, 14, 25, 32)

    private data class Board(val w: Float, val h: Float, val wide: Boolean)

    private fun grid(n: Int, w: Float, h: Float, wide: Boolean = false) =
        stickerGrid(n, w, h, wide = wide, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)

    private fun grid(n: Int, board: Board) = grid(n, board.w, board.h, board.wide)

    /** Whether [grid]'s [n] stickers, their insets and gaps, and their days fit a [w] by [h] board. */
    private fun fits(grid: StickerGrid, n: Int, w: Float, h: Float): Boolean {
        val rows = (n + grid.columns - 1) / grid.columns
        val across = grid.columns * grid.cellWidth + (grid.columns - 1) * grid.gapX + 2 * grid.side
        val label = if (grid.dated) LabelHeight else 0f
        val down = rows * (grid.size + label) + (rows - 1) * grid.gapY + grid.top + grid.side
        return across <= w + Slack && down <= h + Slack
    }

    // Catches the overflow on the report, on a phone and on a tablet (whose card no longer scrolls): stickers, their tilt
    // and peel, and their days running past the board's edges.
    @Test
    fun `every sticker and its day stay inside the board`() {
        for (board in boards) for (n in counts) {
            val grid = grid(n, board)
            assertTrue("n=$n $board $grid", fits(grid, n, board.w, board.h))
            assertTrue("n=$n $board cell narrower than its day", !grid.dated || grid.cellWidth >= LabelWidth)
        }
    }

    // Catches stickers left small on a roomy board: no other column count would give a dense sheet's stickers more room.
    @Test
    fun `a dense sheet's stickers are as large as the board allows`() {
        for (board in boards) for (n in listOf(25, 32)) {
            val grid = grid(n, board)
            if (grid.size >= TabletMost - Slack) continue
            for (columns in 1..n) {
                val larger = grid.size * Larger
                val other = StickerGrid(columns, larger, maxOf(larger, if (grid.dated) LabelWidth else 0f), grid.dated)
                assertFalse("n=$n $board $grid but $columns columns fit ${other.size}", fits(other, n, board.w, board.h))
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
        for (board in boards) for (n in counts) {
            val grid = grid(n, board)
            assertTrue("n=$n $board gapX ${grid.gapX}", grid.gapX in GapLeast * grid.size..GapMost * grid.size)
            if (!grid.dated) assertTrue("n=$n $board gapY ${grid.gapY}", grid.gapY in GapLeast * grid.size..GapMost * grid.size)
        }
    }

    // Catches the insets shrinking to the bare padding: a peeled sticker's corner would touch the edge.
    @Test
    fun `the insets leave room for a tilted, peeled sticker`() {
        for (board in boards) for (n in counts) {
            val grid = grid(n, board)
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

    // Catches the tablet ladder lost where there is room, and a year's sheet sized past a short tablet board: the ladder
    // only caps, and the board's height sets the rest.
    @Test
    fun `a tablet board caps the stickers at the ladder and fits the rest`() {
        val roomy = { n: Int -> grid(n, 788f, 600f, wide = true) }
        assertEquals(176f, roomy(1).size, 0.01f)
        assertEquals(128f, roomy(6).size, 0.01f)
        assertEquals(3, roomy(6).columns)
        assertEquals(7, roomy(14).columns)
        assertEquals(92f, roomy(14).size, 0.01f)
        for (board in boards.filter { it.wide }) for (n in counts) assertTrue("n=$n $board", grid(n, board).size <= TabletLargest)

        val longest = grid(14, 772f, 283f, wide = true)
        assertEquals(7, longest.columns)
        assertEquals(92f, longest.size, 0.01f)
        val year = grid(32, 772f, 265f, wide = true)
        assertEquals(11, year.columns)
        assertEquals(51.5f, year.size, 0.2f)
        assertTrue(year.dated)
        val tall = grid(32, 772f, 460f, wide = true)
        assertEquals(8, tall.columns)
        assertEquals(76.9f, tall.size, 0.2f)
    }

    // Catches a divide by zero or a negative size when there is nothing to lay out or no room at all.
    @Test
    fun `no stickers or no room gives a sane grid`() {
        assertEquals(1, grid(0, 331f, 420f).columns)
        assertTrue(grid(6, 20f, 20f).size >= 0f)
        assertTrue(grid(32, 772f, 0f, wide = true).size >= 0f)
    }
}

/** A day label ("Oct 25", "Sep 25") at labelMedium bold: about 40dp wide, 8dp gap plus a 16dp line. */
private const val LabelWidth = 40f
private const val LabelHeight = 24f

/** The sheet's padding, how far past its square a sticker tilted 9° and scaled 1.08 reaches, and the room left past that. */
private const val Pad = 12f
private const val PeeledReach = 0.118f
private const val EdgeClear = 6f

/** The tablet ladder's caps: past eight stickers, and its largest (one or two alone). */
private const val TabletMost = 92f
private const val TabletLargest = 176f

/** Gaps between 15% and 20% of a sticker; a sticker 2% larger than the chosen one must not fit at any column count. */
private const val GapLeast = 0.15f - 0.001f
private const val GapMost = 0.2f
private const val Larger = 1.02f
private const val Slack = 0.01f
