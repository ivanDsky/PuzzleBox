package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.AdjacencyRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AdjacencyRule (I1): rejects non-4-adjacent steps, passes the four neighbors. */
class AdjacencyRuleTest {

    private val rule = AdjacencyRule()
    private val puzzle = ZipTestSupport.n5Puzzle()
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `rejects every non-adjacent direction`() {
        val cases = listOf(
            "diagonal up-left" to Cell(8), // diff -4
            "diagonal up-right" to Cell(6), // diff -6
            "same row skip" to Cell(14),
            "two down" to Cell(22),
            "two up" to Cell(2),
        )
        cases.forEach { (name, to) ->
            assertTrue(name, rule.violated(ZipMove.Extend(to), state(ZipTestSupport.path(12))))
            assertEquals(
                name,
                ZipRuleId.Adjacency,
                rule.check(ZipMove.Extend(to), state(ZipTestSupport.path(12)))!!.rule,
            )
        }
    }

    @Test
    fun `passes the four neighbors`() {
        listOf(Cell(13), Cell(11), Cell(17), Cell(7)).forEach { to ->
            assertNull("to=$to", rule.check(ZipMove.Extend(to), state(ZipTestSupport.path(12))))
        }
    }

    @Test
    fun `is silent on the empty trace and on non-extend moves`() {
        assertTrue(!rule.violated(ZipMove.Extend(Cell(0)), state(emptyList())))
        assertTrue(!rule.violated(ZipMove.Reset, state(ZipTestSupport.path(12))))
        assertTrue(!rule.violated(ZipMove.RetractTo(Cell(12)), state(ZipTestSupport.path(12))))
    }
}
