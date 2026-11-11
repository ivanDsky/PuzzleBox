package com.ivandsky.puzzlebox.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SolveReplayTest {

    @Test
    fun `equal bytes make equal replays`() {
        val a = SolveReplay.of(byteArrayOf(1, 2, 3))
        val b = SolveReplay.of(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different bytes make different replays`() {
        assertNotEquals(SolveReplay.of(byteArrayOf(1, 2, 3)), SolveReplay.of(byteArrayOf(1, 2, 4)))
        assertNotEquals(SolveReplay.of(byteArrayOf(1, 2)), SolveReplay.of(byteArrayOf(1, 2, 0)))
    }

    @Test
    fun `size is the byte count`() {
        assertEquals(3, SolveReplay.of(byteArrayOf(9, 8, 7))?.size)
    }

    @Test
    fun `input array is copied on the way in`() {
        val input = byteArrayOf(1, 2, 3)
        val replay = SolveReplay.of(input)
        input[0] = 42
        assertArrayEquals(byteArrayOf(1, 2, 3), replay?.toByteArray())
    }

    @Test
    fun `output array is copied on the way out`() {
        val replay = SolveReplay.of(byteArrayOf(1, 2, 3))
        replay?.toByteArray()?.set(0, 42)
        assertArrayEquals(byteArrayOf(1, 2, 3), replay?.toByteArray())
    }

    @Test
    fun `empty bytes give no replay`() {
        assertNull(SolveReplay.of(ByteArray(0)))
    }

    @Test
    fun `bytes over the cap give no replay`() {
        assertNull(SolveReplay.of(ByteArray(SolveReplay.MAX_BYTES + 1)))
    }

    @Test
    fun `bytes at the cap are kept`() {
        val replay = SolveReplay.of(ByteArray(SolveReplay.MAX_BYTES))
        assertNotNull(replay)
        assertEquals(SolveReplay.MAX_BYTES, replay?.size)
    }
}
