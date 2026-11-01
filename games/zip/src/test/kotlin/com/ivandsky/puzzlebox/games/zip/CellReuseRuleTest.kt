package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.CellReuseRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CellReuseRule (I3): no cell twice — rejects any step onto a trace cell. */
class CellReuseRuleTest {

    private val rule = CellReuseRule()
    private val puzzle = ZipTestSupport.n5Puzzle()
    private val trace = ZipTestSupport.path(0, 1, 2, 3, 4)
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `rejects stepping onto any visited cell`() {
        listOf(Cell(0), Cell(1), Cell(2), Cell(3), Cell(4)).forEach { to ->
            assertTrue("to=$to", rule.violated(ZipMove.Extend(to), state(trace)))
            assertEquals(
                "to=$to",
                ZipRuleId.CellReuse,
                rule.check(ZipMove.Extend(to), state(trace))!!.rule,
            )
        }
    }

    @Test
    fun `passes fresh cells`() {
        listOf(Cell(5), Cell(9), Cell(24)).forEach { to ->
            assertNull("to=$to", rule.check(ZipMove.Extend(to), state(trace)))
        }
    }

    @Test
    fun `is silent on non-extend moves`() {
        assertTrue(!rule.violated(ZipMove.Reset, state(trace)))
        assertTrue(!rule.violated(ZipMove.RetractTo(Cell(2)), state(trace)))
    }
}
