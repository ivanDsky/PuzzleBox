package com.ivandsky.puzzlebox.games.strands.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsPocketsTest {
    private val total = StrandsDraft.ROWS * StrandsDraft.COLS

    /** A draft whose paths hold every cell except [open]. */
    private fun draftOpen(vararg open: Int): StrandsDraft {
        val held = (0 until total).filter { it !in open }
        val paths = if (held.isEmpty()) emptyList() else listOf(StrandsDraftPath(held, null))
        return StrandsDraft(".".repeat(total), "", paths, StrandsDraft.NO_SPANGRAM)
    }

    private fun pockets(vararg open: Int) = StrandsPockets.of(draftOpen(*open))

    @Test
    fun `a lone uncovered cell is a pocket`() {
        assertEquals(listOf(setOf(0)), pockets(0))
    }

    @Test
    fun `two touching cells are one pocket`() {
        assertEquals(listOf(setOf(0, 1)), pockets(0, 1))
    }

    @Test
    fun `diagonal neighbours join a three cell pocket`() {
        assertEquals(listOf(setOf(0, 7, 14)), pockets(0, 7, 14))
    }

    @Test
    fun `a four cell area is not a pocket`() {
        assertTrue(pockets(0, 1, 6, 7).isEmpty())
    }

    @Test
    fun `separate small areas are separate pockets`() {
        assertEquals(setOf(setOf(0), setOf(47)), pockets(0, 47).toSet())
    }

    @Test
    fun `a board with no paths has no pocket`() {
        assertTrue(StrandsPockets.of(draftOpen(*IntArray(total) { it })).isEmpty())
    }

    @Test
    fun `a fully covered board has no pocket`() {
        assertTrue(pockets().isEmpty())
    }
}
