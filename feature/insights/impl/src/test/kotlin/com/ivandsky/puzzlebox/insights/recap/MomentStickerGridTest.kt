package com.ivandsky.puzzlebox.insights.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentStickerGridTest {

    // Phone sheets as the card gives them: the golden phone's heights, the reported split-screen board, a short one.
    private val phone = (300..460 step 40).map { 363f to it.toFloat() } + listOf(353f to 302f, 330f to 250f)
    private val counts = listOf(1, 2, 4, 6, 14, 25, 32)

    // Catches the overflow on the report: stickers, their tilt and peel, and their days running past the board's edges.
    @Test
    fun `every sticker and its day stay inside a phone board`() {
        for ((w, h) in phone) for (n in counts) {
            val grid = stickerGrid(n, w, h, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
            val rows = (n + grid.columns - 1) / grid.columns
            val where = "n=$n ${w}x$h $grid"
            val across = grid.columns * grid.cellWidth + (grid.columns - 1) * StickerGapX + 2 * grid.side
            assertTrue("$where across $across > $w", across <= w + Slack)
            val label = if (grid.dated) LabelHeight else 0f
            val down = rows * (grid.size + label) + (rows - 1) * StickerGapY + grid.top + grid.side
            assertTrue("$where down $down > $h", down <= h + Slack)
            assertTrue("$where cell narrower than its day", !grid.dated || grid.cellWidth >= LabelWidth)
        }
    }

    // Catches the insets shrinking to the bare padding: a peeled sticker's corner would touch the edge.
    @Test
    fun `the insets leave room for a tilted, peeled sticker`() {
        for (n in counts) {
            val grid = stickerGrid(n, 363f, 420f, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
            assertTrue("n=$n side ${grid.side}", grid.side >= Pad + PeeledReach * grid.size)
            assertTrue("n=$n top ${grid.top}", grid.top >= grid.side + 5f)
        }
    }

    // Catches the fit breaking the reference layouts: 6 in 3 columns at 68dp, and 14 in 5 columns on a phone.
    @Test
    fun `a phone keeps the reference columns and caps`() {
        val six = stickerGrid(6, 363f, 420f, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
        assertEquals(3, six.columns)
        assertEquals(68f, six.size, 0.01f)
        assertTrue(six.dated)
        val alone = stickerGrid(1, 363f, 420f, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
        assertEquals(120f, alone.size, 0.01f)
        for (h in listOf(380f, 420f, 460f)) {
            val fourteen = stickerGrid(14, 363f, h, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
            assertEquals("h=$h", 5, fourteen.columns)
            assertTrue("h=$h", fourteen.dated)
        }
    }

    // Catches a year's stickers shrunk to specks under their days: a dense sheet drops the days and stays readable.
    @Test
    fun `a year of stickers drops the days and stays readable on a phone`() {
        for ((w, h) in phone.filter { it.second >= 300f }) {
            val grid = stickerGrid(32, w, h, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
            assertFalse("${w}x$h", grid.dated)
            assertTrue("${w}x$h size ${grid.size}", grid.size >= MinReadable)
        }
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
        val none = stickerGrid(0, 363f, 420f, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
        assertEquals(1, none.columns)
        val cramped = stickerGrid(6, 20f, 20f, wide = false, labelWidthDp = LabelWidth, labelHeightDp = LabelHeight)
        assertTrue(cramped.size >= 0f)
    }
}

/** A day label ("Oct 25", "Sep 25") at labelMedium bold: about 40dp wide, 8dp gap plus a 16dp line. */
private const val LabelWidth = 40f
private const val LabelHeight = 24f

/** The sheet's padding, and how far past its square a sticker tilted 8.5° and scaled 1.08 reaches. */
private const val Pad = 12f
private const val PeeledReach = 0.114f

/** 32 stickers on the reported 353 by 302 board can be about 39dp at most; below 36 the glyphs blur together. */
private const val MinReadable = 36f
private const val Slack = 0.01f
