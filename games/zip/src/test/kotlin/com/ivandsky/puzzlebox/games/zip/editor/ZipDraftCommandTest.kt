package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zip edits as undoable commands: apply/revert round-trips, waypoint order, refused walls, codec tags. */
class ZipDraftCommandTest {

    private fun wall(cell: Int, dir: WallDirection) = Wall(Cell(cell), dir)

    /** 4×4 snake across the top two rows: 0 1 2 3 / 7 6 5 4, numbered at 0, 2, 4. */
    private fun snake() = ZipDraft(4, emptySet(), listOf(0, 1, 2, 3, 7, 6, 5, 4), listOf(0, 2, 4))

    private fun blank(n: Int = 4) = ZipDraft(n, emptySet(), emptyList(), emptyList())

    private fun assertRoundTrips(draft: ZipDraft, command: Command<ZipDraft>) =
        assertEquals(draft, command.revert(command.apply(draft)))

    @Test
    fun `a stroke replaces the path and keeps surviving marks in path order`() {
        val draft = snake()
        val stroke = draft.stroke(listOf(4, 5, 2, 1, 0))

        val after = stroke.apply(draft)

        assertEquals(listOf(4, 5, 2, 1, 0), after.path)
        assertEquals(listOf(4, 2, 0), after.waypoints)
        assertRoundTrips(draft, stroke)
    }

    @Test
    fun `a stroke drops marks the new path no longer visits`() {
        val draft = snake()

        val after = draft.stroke(listOf(0, 1, 5)).apply(draft)

        assertEquals(listOf(0, 5), after.waypoints)
    }

    @Test
    fun `a stroke on a path-less board numbers its ends and orders imported marks`() {
        val draft = blank().copy(waypoints = listOf(9, 1, 5))

        val after = draft.stroke(listOf(1, 5, 9, 10)).apply(draft)

        assertEquals(listOf(1, 5, 9, 10), after.waypoints)
        assertRoundTrips(draft, draft.stroke(listOf(1, 5, 9, 10)))
    }

    @Test
    fun `a one-cell stroke numbers that cell once`() {
        val draft = blank()

        assertEquals(listOf(6), draft.stroke(listOf(6)).apply(draft).waypoints)
    }

    @Test
    fun `reverse flips the path and renumbers in the new order`() {
        val draft = snake().copy(waypoints = listOf(0, 3, 4))
        val reversed = draft.reversed()

        val after = reversed.apply(draft)

        assertEquals(listOf(4, 5, 6, 7, 3, 2, 1, 0), after.path)
        assertEquals(listOf(4, 3, 0), after.waypoints)
        assertRoundTrips(draft, reversed)
    }

    @Test
    fun `clear path is one stroke that leaves the numbered cells alone`() {
        val draft = snake()
        val cleared = draft.stroke(emptyList())

        assertEquals(draft.copy(path = emptyList()), cleared.apply(draft))
        assertRoundTrips(draft, cleared)
    }

    @Test
    fun `toggle mark numbers an interior path cell in path order and removes it again`() {
        val draft = snake()

        val on = draft.toggleMark(7)!!
        val numbered = on.apply(draft)
        assertEquals(listOf(0, 2, 7, 4), numbered.waypoints)
        assertRoundTrips(draft, on)

        val off = numbered.toggleMark(7)!!
        assertEquals(draft, off.apply(numbered))
        assertRoundTrips(numbered, off)
    }

    @Test
    fun `toggle mark refuses a cell off the path and either endpoint`() {
        val draft = snake().copy(path = listOf(0, 1, 2, 3, 7), waypoints = listOf(0, 7))

        assertNull(draft.toggleMark(15))
        assertNull(draft.toggleMark(0))
        assertNull(draft.toggleMark(7))
        assertNull(blank().toggleMark(0))
    }

    @Test
    fun `toggle wall adds then removes the wall`() {
        val draft = snake()
        val w = wall(8, WallDirection.RIGHT)

        val add = draft.toggleWall(w)!!
        val walled = add.apply(draft)
        assertEquals(setOf(w), walled.walls)
        assertRoundTrips(draft, add)

        val remove = walled.toggleWall(w)!!
        assertEquals(draft, remove.apply(walled))
        assertRoundTrips(walled, remove)
    }

    @Test
    fun `toggle wall refuses an edge between consecutive path cells in either direction`() {
        val draft = snake()

        assertNull(draft.toggleWall(wall(0, WallDirection.RIGHT)))
        assertNull(draft.toggleWall(wall(3, WallDirection.DOWN)))
        assertNull(draft.toggleWall(wall(6, WallDirection.RIGHT)))
        assertNull(draft.toggleWall(wall(5, WallDirection.RIGHT)))
        assertNotNull(draft.toggleWall(wall(0, WallDirection.DOWN)))
        assertNotNull(draft.toggleWall(wall(1, WallDirection.DOWN)))
    }

    @Test
    fun `toggle wall refuses an edge that is not between two cells`() {
        val draft = blank()

        assertNull(draft.toggleWall(wall(3, WallDirection.RIGHT)))
        assertNull(draft.toggleWall(wall(12, WallDirection.DOWN)))
    }

    @Test
    fun `clear walls is one step and reverts every wall`() {
        val walls = setOf(wall(0, WallDirection.DOWN), wall(5, WallDirection.RIGHT))
        val draft = snake().copy(walls = walls)
        val clear = ZipDraftCommand.ClearWalls(draft.walls)

        assertEquals(emptySet<Wall>(), clear.apply(draft).walls)
        assertRoundTrips(draft, clear)
    }

    private fun history(): DraftState<ZipDraft> {
        val start = blank()
        val stroke = start.stroke(listOf(0, 1, 2, 3, 7, 6, 5, 4))
        val d1 = stroke.apply(start)
        val mark = d1.toggleMark(7)!!
        val d2 = mark.apply(d1)
        val wallCmd = d2.toggleWall(wall(8, WallDirection.RIGHT))!!
        val d3 = wallCmd.apply(d2)
        val clear = ZipDraftCommand.ClearWalls(d3.walls)
        val d4 = clear.apply(d3)
        val reverse = d4.reversed()
        val d5 = reverse.apply(d4)
        val resize = ReplaceDraft(d5, blank(5))
        val undone = d5.stroke(emptyList())
        return DraftState(
            resize.after,
            CommandHistory(listOf(stroke, mark, wallCmd, clear, reverse, resize), listOf(undone, mark)),
        )
    }

    @Test
    fun `a history of every command round-trips through the codec`() {
        val state = history()

        val decoded = (ZipDraftCodec.decode(ZipDraftCodec.encode(state)) as DecodeResult.Ok).value

        assertEquals(state, decoded)
    }

    @Test
    fun `a decoded history undoes back to the blank board`() {
        val decoded = (ZipDraftCodec.decode(ZipDraftCodec.encode(history())) as DecodeResult.Ok).value
        var state = decoded.draft
        var h = decoded.history

        while (true) {
            val step = h.undo(state) ?: break
            state = step.state
            h = step.history
        }

        assertEquals(blank(), state)
    }

    @Test
    fun `a command cell off the board it meets is malformed`() {
        val bad = ZipDraftCommand.ToggleMark(cell = 20, waypointsBefore = listOf(0, 3), waypointsAfter = listOf(0, 20, 3))
        val bytes = ZipDraftCodec.encode(DraftState(blank(4), CommandHistory(listOf(bad))))

        val result = ZipDraftCodec.decode(bytes)

        assertTrue("got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    @Test
    fun `a truncated command step is malformed`() {
        val bytes = ZipDraftCodec.encode(history())

        val result = ZipDraftCodec.decode(bytes.copyOf(bytes.size - 1))

        assertTrue("got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }
}
