package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.WaypointOrderRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WaypointOrderRule (I5): waypoints must be entered in label order. */
class WaypointOrderRuleTest {

    private val rule = WaypointOrderRule()
    private val puzzle = ZipTestSupport.n5Puzzle() // waypoints 0 (1), 12 (2), 24 (3)
    private val inOrder = ZipTestSupport.path(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11)
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `rejects skipping ahead to a later waypoint`() {
        // waypoint 2 (cell 12) unvisited but the player heads for waypoint 3 (cell 24).
        val ahead = ZipTestSupport.path(0, 5, 10, 15, 20, 21, 22, 23)
        assertTrue(rule.violated(ZipMove.Extend(Cell(24)), state(ahead)))
        assertEquals(
            ZipRuleId.WaypointOrder,
            rule.check(ZipMove.Extend(Cell(24)), state(ahead))!!.rule,
        )
    }

    @Test
    fun `passes in-order waypoints and plain cells`() {
        assertNull(rule.check(ZipMove.Extend(Cell(12)), state(inOrder))) // label 2, expected 2
        assertNull(rule.check(ZipMove.Extend(Cell(13)), state(inOrder))) // not a waypoint
    }

    @Test
    fun `is silent on non-extend moves`() {
        assertTrue(!rule.violated(ZipMove.Reset, state(inOrder)))
        assertTrue(!rule.violated(ZipMove.RetractTo(Cell(0)), state(inOrder)))
    }
}
