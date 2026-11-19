package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.gameui.TraceTip
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Zip's live tip (spec A4) over the real [ZipDragHooks]: the line end sits at the point
 * nearest the finger on a step Zip would accept — never through a wall, off the board, over
 * a used cell or an out-of-order waypoint, and never diagonally — and stays on the axis the
 * finger travels along while it passes a cell centre.
 */
class ZipTipAimTest {

    // 5x5; waypoint 1 at (0,0), 2 at (0,3), 3 at (1,1); a wall right of (2,0).
    private val board = ZipBoard(
        n = 5,
        walls = setOf(Wall(Cell(10), WallDirection.RIGHT)),
        waypoints = listOf(Cell(0), Cell(3), Cell(6)),
    )
    private val hooks = ZipDragHooks(board)

    // Every row walled off from the next but at its ends: mid-row only east and west are legal.
    private val walledHooks = ZipDragHooks(ZipPlayFixture.board())

    private fun cells(vararg rc: Pair<Int, Int>) = rc.map { (r, c) -> GridCell(r, c) }

    /** Finger [dx], [dy] cells away from the centre of the tail. */
    private fun finger(tail: GridCell, dx: Float, dy: Float) =
        Offset(tail.col + 0.5f + dx, tail.row + 0.5f + dy)

    /** A finger landing at [dx], [dy]: a fresh aim, with no travel behind it. */
    private fun tip(path: List<GridCell>, dx: Float, dy: Float, by: ZipDragHooks = hooks): TraceTip<GridCell> =
        checkNotNull(ZipTipAim().tip(path, finger(path.last(), dx, dy), by)) { "no tip at ($dx, $dy)" }

    private fun ZipTipAim.at(path: List<GridCell>, dx: Float, dy: Float): TraceTip<GridCell> =
        checkNotNull(tip(path, finger(path.last(), dx, dy), hooks)) { "no tip at ($dx, $dy)" }

    private fun assertTip(kind: TipKind, toward: GridCell, extent: Float, tip: TraceTip<GridCell>) {
        assertEquals(kind, tip.kind)
        assertEquals(toward, tip.toward)
        assertEquals(extent, tip.extent, 1e-4f)
    }

    @Test
    fun `toward a legal cell the tip follows the finger on the axis, capped short of it`() {
        assertTip(TipKind.Forward, GridCell(0, 1), 0.3f, tip(cells(0 to 0), 0.3f, 0.04f))
        assertEquals(MotionTokens.Trace.TipMaxCells, tip(cells(0 to 0), 3f, 0f).extent, 1e-5f)
    }

    @Test
    fun `the perpendicular part of the finger moves the tip nowhere off the axis`() {
        // Mostly east with a quarter cell of south on top: the lean is the east push alone.
        assertTip(TipKind.Forward, GridCell(0, 1), 0.3f, tip(cells(0 to 0), 0.3f, 0.25f))
    }

    @Test
    fun `between two legal steps the tip takes the one the finger is nearer`() {
        assertTip(TipKind.Forward, GridCell(0, 1), 0.45f, tip(cells(0 to 0), 0.45f, 0.44f))
        assertTip(TipKind.Forward, GridCell(1, 0), 0.45f, tip(cells(0 to 0), 0.44f, 0.45f))
    }

    @Test
    fun `toward the previous cell the tip pulls back`() {
        assertTip(TipKind.Back, GridCell(0, 0), 0.35f, tip(cells(0 to 0, 0 to 1), -0.35f, 0f))
    }

    @Test
    fun `beside a wall the tip takes the legal step and never leans through the wall`() {
        // Right of the tail (2,0) is walled; down to (3,0) is open.
        val path = cells(0 to 0, 1 to 0, 2 to 0)
        assertTip(TipKind.Forward, GridCell(3, 0), 0.2f, tip(path, 0.3f, 0.2f))
        assertEquals(0f, tip(path, 0.9f, 0f).extent, 0f)
    }

    @Test
    fun `a finger off the centre line of a walled row still moves the tip along the row`() {
        // Ticket 02b: the tip named the finger's dominant axis first and tested the step after,
        // so a finger 0.2 below the centre line lost the tip for 0.2 either side of every
        // centre (south is walled) and the end stood still there while the finger went on.
        val path = cells(0 to 0, 0 to 1)
        assertTip(TipKind.Back, GridCell(0, 0), 0.05f, tip(path, -0.05f, 0.2f, walledHooks))
        assertTip(TipKind.Forward, GridCell(0, 2), 0.05f, tip(path, 0.05f, 0.2f, walledHooks))
    }

    @Test
    fun `the tip follows the finger from the tail centre itself`() {
        // Ticket 02b: a 0.06-cell dead zone round every centre held the end still under a
        // slow finger.
        assertTip(TipKind.Forward, GridCell(0, 1), 0.03f, tip(cells(0 to 0), 0.03f, 0.02f))
    }

    @Test
    fun `toward the board edge, a used cell or an out-of-order waypoint the tip rests on the tail`() {
        assertEquals(0f, tip(cells(0 to 0), 0f, -2f).extent, 0f)
        // Tail (1,0): up is (0,0), already traced and not the previous cell.
        assertEquals(0f, tip(cells(0 to 0, 0 to 1, 1 to 1, 1 to 0), 0f, -0.8f).extent, 0f)
        // Tail (1,0) right next to waypoint 3 at (1,1) before waypoint 2 at (0,3) is reached.
        assertEquals(0f, tip(cells(0 to 0, 1 to 0), 0.8f, 0f).extent, 0f)
    }

    @Test
    fun `a finger clearly inside a blocked cell holds the tip on the tail rather than leaning it into the side step`() {
        // Ticket 1572: deep in an out-of-order number, a finger drifting toward the open side
        // leaned the end into it, which read as the line going there before it had.
        // Tail (1,0): east is waypoint 3 before waypoint 2; south (2,0) is open.
        val path = cells(0 to 0, 1 to 0)
        assertEquals(0f, tip(path, 1.2f, 0.55f).extent, 0f)
        assertTip(TipKind.Forward, GridCell(2, 0), 0.3f, tip(path, 0.6f, 0.3f))
    }

    @Test
    fun `no path or no finger makes no tip`() {
        assertNull(ZipTipAim().tip(emptyList(), Offset(0.5f, 0.5f), hooks))
        assertNull(ZipTipAim().tip(cells(0 to 0), null, hooks))
    }

    @Test
    fun `a finger travelling along a row keeps the tip on the row past a centre with an open side`() {
        // Ticket 02b: 0.2 below the centre line and nearer the centre than that, south was
        // the dominant axis, so the end jumped to the centre and poked at (1,2) until the
        // finger was 0.2 past it.
        val aim = ZipTipAim()
        val path = cells(0 to 0, 0 to 1, 0 to 2)
        assertTip(TipKind.Back, GridCell(0, 1), 0.4f, aim.at(path, -0.4f, 0.2f))
        assertTip(TipKind.Back, GridCell(0, 1), 0.3f, aim.at(path, -0.3f, 0.2f))
        assertTip(TipKind.Back, GridCell(0, 1), 0.2f, aim.at(path, -0.2f, 0.2f))
        assertTip(TipKind.Back, GridCell(0, 1), 0.1f, aim.at(path, -0.1f, 0.2f))
        val onCentre = aim.at(path, 0f, 0.2f)
        assertEquals("on the row", 0, onCentre.toward.row)
        assertEquals(0f, onCentre.extent, 0f)
        assertTip(TipKind.Forward, GridCell(0, 3), 0.1f, aim.at(path, 0.1f, 0.2f))
        assertTip(TipKind.Forward, GridCell(0, 3), 0.2f, aim.at(path, 0.2f, 0.2f))
    }

    @Test
    fun `a finger that turns at the centre takes the tip onto the new axis`() {
        val aim = ZipTipAim()
        val path = cells(0 to 0, 0 to 1, 0 to 2)
        assertTip(TipKind.Back, GridCell(0, 1), 0.2f, aim.at(path, -0.2f, 0f))
        assertTip(TipKind.Back, GridCell(0, 1), 0.1f, aim.at(path, -0.1f, 0f))
        assertEquals(0f, aim.at(path, 0f, 0f).extent, 0f)
        aim.at(path, 0f, 0.1f) // the turn is not yet told from a wobble
        assertTip(TipKind.Forward, GridCell(1, 2), 0.2f, aim.at(path, 0f, 0.2f))
        assertTip(TipKind.Forward, GridCell(1, 2), 0.3f, aim.at(path, 0f, 0.3f))
    }

    @Test
    fun `a lift forgets the axis, so the next finger takes the nearest step`() {
        val aim = ZipTipAim()
        val path = cells(0 to 0, 0 to 1, 0 to 2)
        assertTip(TipKind.Back, GridCell(0, 1), 0.3f, aim.at(path, -0.3f, 0.2f))
        assertNull(aim.tip(path, null, hooks))

        assertTip(TipKind.Forward, GridCell(1, 2), 0.2f, aim.at(path, -0.1f, 0.2f))
    }

    @Test
    fun `a lift forgets the heading, so the next finger turns on its own travel alone`() {
        val aim = ZipTipAim()
        val path = cells(0 to 0, 0 to 1, 0 to 2)
        aim.at(path, -0.4f, 0.2f)
        assertNull(aim.tip(path, null, hooks))

        // Lands east of the centre, then moves straight down. Measured from where the last
        // drag left off, that travel would still read as eastward and hold the tip on the row.
        assertTip(TipKind.Forward, GridCell(0, 3), 0.25f, aim.at(path, 0.25f, 0.2f))
        assertTip(TipKind.Forward, GridCell(1, 2), 0.28f, aim.at(path, 0.25f, 0.28f))
    }

    @Test
    fun `a tail with no step on the travelled axis gives the tip to the nearest legal step`() {
        val aim = ZipTipAim()
        assertTip(TipKind.Forward, GridCell(0, 1), 0.3f, aim.at(cells(0 to 0), 0.3f, 0f))

        // Tail (1,0): east is an out-of-order waypoint and west is off the board.
        assertTip(TipKind.Forward, GridCell(2, 0), 0.1f, aim.at(cells(0 to 0, 1 to 0), 0.3f, 0.1f))
    }
}
