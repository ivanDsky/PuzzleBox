package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every id the Queens engine can hand the UI has copy (00-interfaces P-4): a rung added to
 * [QueensLadder.Default] without a [QueensRungCopy] caption fails here. 25 extends this
 * over the analyzer's reason ids and the publish gates' block ids.
 */
class QueensCopyCoverageTest {

    @Test
    fun `every rung of the default ladder has a caption`() {
        val rungs = QueensLadder.Default.rungs
        assertTrue("the default ladder lists its rungs", rungs.isNotEmpty())

        val missing = rungs.filter { QueensRungCopy.caption(it.captionKey) == null }.map { it.id }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an unknown caption key has no caption`() {
        assertEquals(null, QueensRungCopy.caption("queens.hint.rung.unknown"))
    }
}
