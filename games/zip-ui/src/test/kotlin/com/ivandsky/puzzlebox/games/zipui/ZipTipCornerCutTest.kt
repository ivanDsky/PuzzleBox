package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zip's line end under a fast finger (#1572): drawn fast, a finger cuts each corner on the
 * diagonal instead of passing through the corner cell's centre. The end must come round the
 * corner with it, not wait on the old axis and then snap round once the next cell commits.
 * Frames are 0.1 cell of travel apart, a fast finger at 60 fps; the stroke commits the next
 * cell only 0.55 past the corner centre, so the tail stays on the corner throughout.
 */
class ZipTipCornerCutTest {

    // Phone recording, Zip #3 (6x6): 1 (3,3) and 2 (4,4) as (col, row); the line turns south at (4,3) toward 2.
    private val phone = ZipDragHooks(ZipBoard(n = 6, walls = emptySet(), waypoints = listOf(21, 28, 25, 3, 24, 29, 4, 20).map(::Cell)))

    // Tablet recording, Zip #527 (7x7): 1 (4,0) and 2 (5,2) as (col, row); the line turns east at (4,2) toward 2.
    private val tablet = ZipDragHooks(
        ZipBoard(n = 7, walls = emptySet(), waypoints = listOf(4, 19, 5, 45, 33, 26, 31, 24, 17, 44, 43, 29, 3, 15, 22).map(::Cell)),
    )

    /** The finger at each offset from the tail centre in turn; returns how far the drawn end trails it at worst. */
    private fun worstLag(path: List<GridCell>, hooks: ZipDragHooks, offsets: List<Offset>): Float {
        val aim = ZipTipAim()
        val tail = path.last()
        val centre = Offset(tail.col + 0.5f, tail.row + 0.5f)
        return offsets.maxOf { offset ->
            val tip = aim.tip(path, centre + offset, hooks)
            val end = if (tip == null) {
                centre
            } else {
                centre + Offset((tip.toward.col - tail.col).toFloat(), (tip.toward.row - tail.row).toFloat()) * tip.extent
            }
            (centre + offset - end).getDistance()
        }
    }

    /** A straight cut from [from] to [to] (offsets from the corner centre), one frame per ~0.1 cell. */
    private fun cut(from: Offset, to: Offset) = (0..6).map { from + (to - from) * (it / 6f) }

    @Test
    fun `a fast finger cutting the phone corner toward 2 keeps the line end within reach of it`() {
        // Bug it catches: the end waits on the row, 0.45 off the finger, and snaps round when 2 commits.
        val path = listOf(GridCell(3, 3), GridCell(3, 4))
        val lag = worstLag(path, phone, cut(Offset(-0.45f, 0f), Offset(0f, 0.45f)))
        assertTrue("the end trails the finger by $lag cell", lag <= 0.3f)
    }

    @Test
    fun `a fast finger cutting the tablet corner toward 2 keeps the line end within reach of it`() {
        // Bug it catches: the end waits on the column, 0.45 off the finger, and snaps round when 2 commits.
        val path = listOf(GridCell(0, 4), GridCell(1, 4), GridCell(2, 4))
        val lag = worstLag(path, tablet, cut(Offset(0f, -0.45f), Offset(0.45f, 0f)))
        assertTrue("the end trails the finger by $lag cell", lag <= 0.3f)
    }

    @Test
    fun `a finger riding beside a straight run with a wobbling heading keeps the line end on the run`() {
        // Bug it catches: a turn rule eager enough to poke the end sideways off a straight run at a centre.
        val path = listOf(GridCell(3, 3), GridCell(3, 4))
        val aim = ZipTipAim()
        val tail = path.last()
        listOf(-0.4f to 0.2f, -0.3f to 0.23f, -0.2f to 0.2f, -0.1f to 0.23f, 0f to 0.2f, 0.1f to 0.23f).forEach { (dx, dy) ->
            val tip = checkNotNull(aim.tip(path, Offset(tail.col + 0.5f + dx, tail.row + 0.5f + dy), phone))
            assertEquals("on the row at ($dx, $dy)", tail.row, tip.toward.row)
        }
    }
}
