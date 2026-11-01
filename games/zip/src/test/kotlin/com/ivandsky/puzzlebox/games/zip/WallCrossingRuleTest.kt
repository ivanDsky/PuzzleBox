package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.WallCrossingRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WallCrossingRule (I2): rejects steps across a wall, passes open edges. */
class WallCrossingRuleTest {

    private val rule = WallCrossingRule()
    private val puzzle = ZipTestSupport.n5Puzzle()
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `rejects crossing a wall`() {
        // DOWN walls sit on every column except col 4 (the boustrophedon corridors).
        listOf(Cell(0), Cell(1), Cell(12), Cell(19)).forEach { from ->
            val down = Cell(from.idx + 5)
            assertTrue("from=$from", rule.violated(ZipMove.Extend(down), state(listOf(from))))
            assertEquals(
                "from=$from",
                ZipRuleId.WallCrossing,
                rule.check(ZipMove.Extend(down), state(listOf(from)))!!.rule,
            )
        }
    }

    @Test
    fun `passes open edges including the solution corridor`() {
        listOf(
            ZipTestSupport.path(0) to Cell(1), // same row, RIGHT walls do not exist
            ZipTestSupport.path(4) to Cell(9), // col 4: no DOWN wall (solution edge)
            ZipTestSupport.path(12) to Cell(13),
        ).forEach { (path, to) ->
            assertNull("path=$path to=$to", rule.check(ZipMove.Extend(to), state(path)))
        }
    }

    @Test
    fun `is silent on the empty trace and on non-extend moves`() {
        assertTrue(!rule.violated(ZipMove.Extend(Cell(5)), state(emptyList())))
        assertTrue(!rule.violated(ZipMove.Reset, state(ZipTestSupport.path(0))))
    }
}
