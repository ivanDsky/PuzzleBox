package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.StartCellRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** StartCellRule (I4): the empty trace may only start on waypoint 1. */
class StartCellRuleTest {

    private val rule = StartCellRule()
    private val puzzle = ZipTestSupport.n5Puzzle() // waypoints 0, 12, 24
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `rejects starting anywhere but waypoint 1`() {
        listOf(Cell(5), Cell(12), Cell(24), Cell(7)).forEach { to ->
            assertTrue("to=$to", rule.violated(ZipMove.Extend(to), state(emptyList())))
            assertEquals(
                "to=$to",
                ZipRuleId.StartCell,
                rule.check(ZipMove.Extend(to), state(emptyList()))!!.rule,
            )
        }
    }

    @Test
    fun `passes waypoint 1 as the first step`() {
        assertNull(rule.check(ZipMove.Extend(Cell(0)), state(emptyList())))
    }

    @Test
    fun `is silent once the trace exists`() {
        val started = state(ZipTestSupport.path(0))
        assertTrue(!rule.violated(ZipMove.Extend(Cell(1)), started))
        assertTrue(!rule.violated(ZipMove.Reset, started))
    }
}
