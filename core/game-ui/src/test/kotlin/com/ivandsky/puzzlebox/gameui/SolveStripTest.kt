package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.replay.MoveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolveStripTest {
    private val n = STRIP_NORMAL
    private val e = STRIP_EMPTY

    private fun strip(vararg ticks: Pair<Long, MoveKind>, endMs: Long = 100_000) =
        solveStrip(ticks.map { it.first }.toLongArray(), ticks.map { it.second }.toTypedArray(), endMs)

    @Test
    fun `the kind order is Normal, Undo, Hint, Mistake, so the worst-kind rule holds`() {
        assertEquals(listOf(MoveKind.Normal, MoveKind.Undo, MoveKind.Hint, MoveKind.Mistake), MoveKind.entries)
    }

    @Test
    fun `no ticks or no time gives no strip`() {
        assertNull(solveStrip(longArrayOf(), arrayOf(), 100_000))
        assertNull(strip(5_000L to MoveKind.Normal, endMs = 0))
        assertNull(strip(5_000L to MoveKind.Normal, endMs = -1))
    }

    @Test
    fun `a lone mistake in the first tenth starts the strip red and it is ten squares long`() {
        val result = checkNotNull(strip(2_000L to MoveKind.Mistake))
        assertEquals(STRIP_MISTAKE + e.repeat(9), result)
        assertEquals(10, result.codePointCount(0, result.length))
    }

    @Test
    fun `a square shows the worst kind of its ticks`() {
        assertEquals(STRIP_MISTAKE + e.repeat(9), strip(1_000L to MoveKind.Normal, 2_000L to MoveKind.Mistake, 3_000L to MoveKind.Hint))
        assertEquals(STRIP_HINT + e.repeat(9), strip(1_000L to MoveKind.Undo, 2_000L to MoveKind.Hint, 3_000L to MoveKind.Normal))
        assertEquals(STRIP_UNDO + e.repeat(9), strip(1_000L to MoveKind.Normal, 2_000L to MoveKind.Undo))
    }

    @Test
    fun `ticks land in the tenth they fall in, and the end of the solve is the last square`() {
        assertEquals(n + n + e.repeat(7) + n, strip(0L to MoveKind.Normal, 10_000L to MoveKind.Normal, 100_000L to MoveKind.Normal))
        assertEquals(e.repeat(9) + n, strip(99_999L to MoveKind.Normal))
        assertEquals(e.repeat(9) + STRIP_UNDO, strip(250_000L to MoveKind.Undo))
    }

    @Test
    fun `each kind has its own square`() {
        val kinds = MoveKind.entries
        val result = checkNotNull(strip(*kinds.mapIndexed { i, kind -> (i * 10_000L) to kind }.toTypedArray()))
        assertEquals(n + STRIP_UNDO + STRIP_HINT + STRIP_MISTAKE + e.repeat(6), result)
    }
}
