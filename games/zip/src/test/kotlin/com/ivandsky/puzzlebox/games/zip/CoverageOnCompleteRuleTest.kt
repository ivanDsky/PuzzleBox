package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.CoverageOnCompleteRule
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CoverageOnCompleteRule (I7): only the full board counts as covering coverage. */
class CoverageOnCompleteRuleTest {

    private val rule = CoverageOnCompleteRule()
    private val puzzle = ZipTestSupport.n5Puzzle()
    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    @Test
    fun `violates any trace short of the full board`() {
        listOf(
            emptyList<Cell>(),
            ZipTestSupport.path(0),
            ZipTestSupport.n5Solution.dropLast(1),
        ).forEach { path ->
            assertTrue("size=${path.size}", rule.violated(ZipMove.Reset, state(path)))
            assertEquals(
                "size=${path.size}",
                ZipRuleId.CoverageOnComplete,
                rule.check(ZipMove.Reset, state(path))!!.rule,
            )
        }
    }

    @Test
    fun `passes the full board`() {
        assertNull(rule.check(ZipMove.Reset, state(ZipTestSupport.n5Solution)))
    }
}
