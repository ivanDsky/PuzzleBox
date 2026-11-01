package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.EndAtLastWaypointRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** EndAtLastWaypointRule (I6): a FULL trace must end at the final waypoint. */
class EndAtLastWaypointRuleTest {

    private val rule = EndAtLastWaypointRule()
    private val puzzle = ZipTestSupport.n5Puzzle()
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `violates a full trace ending off the last waypoint`() {
        val wrongEnd = ZipTestSupport.n5Solution.toMutableList().also { it[it.lastIndex] = Cell(23) }
        assertTrue(rule.violated(ZipMove.Reset, state(wrongEnd)))
        assertEquals(
            ZipRuleId.EndAtLastWaypoint,
            rule.check(ZipMove.Reset, state(wrongEnd))!!.rule,
        )
    }

    @Test
    fun `passes the solution and is vacuous mid-game`() {
        assertNull(rule.check(ZipMove.Reset, state(ZipTestSupport.n5Solution)))
        listOf(
            emptyList<Cell>(),
            ZipTestSupport.path(0),
            ZipTestSupport.path(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11),
            ZipTestSupport.n5Solution.dropLast(1), // full minus one: not yet judged
        ).forEach { path ->
            assertTrue("size=${path.size}", !rule.violated(ZipMove.Reset, state(path)))
        }
    }
}
