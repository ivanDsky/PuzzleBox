package com.ivandsky.puzzlebox.game.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReplayMoveTest {

    @Test
    fun `moves with equal kind and equal args content are equal`() {
        assertEquals(ReplayMove(kind = 3, args = intArrayOf(1, 2)), ReplayMove(kind = 3, args = intArrayOf(1, 2)))
    }

    @Test
    fun `moves with different args content are not equal`() {
        assertNotEquals(ReplayMove(kind = 3, args = intArrayOf(1, 2)), ReplayMove(kind = 3, args = intArrayOf(2, 1)))
    }

    @Test
    fun `moves with different kind are not equal`() {
        assertNotEquals(ReplayMove(kind = 3, args = intArrayOf(1)), ReplayMove(kind = 4, args = intArrayOf(1)))
    }

    @Test
    fun `moves with empty args are equal`() {
        assertEquals(ReplayMove(kind = 0, args = IntArray(0)), ReplayMove(kind = 0, args = IntArray(0)))
    }

    @Test
    fun `facts with equal fields are equal`() {
        assertEquals(
            ReplayFacts(hints = 1, correct = 4, total = 9, solved = false),
            ReplayFacts(hints = 1, correct = 4, total = 9, solved = false),
        )
    }

    @Test
    fun `facts differing in any field are not equal`() {
        val base = ReplayFacts(hints = 1, correct = 4, total = 9, solved = false)
        assertNotEquals(base, base.copy(hints = 2))
        assertNotEquals(base, base.copy(correct = 5))
        assertNotEquals(base, base.copy(total = 10))
        assertNotEquals(base, base.copy(solved = true))
    }
}
