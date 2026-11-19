package com.ivandsky.puzzlebox.play.domain.random

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffledIndicesTest {
    private fun draw(count: Int, seed: Int): List<Int> = ShuffledIndices(count, Random(seed)).asSequence().toList()

    @Test
    fun `a full draw is every index once, in an order that differs per seed`() {
        val orders = (1..20).map { draw(50, it) }
        orders.forEach { assertEquals((0 until 50).toList(), it.sorted()) }
        assertTrue(orders.distinct().size > 15)
        assertNotEquals((0 until 50).toList(), orders.first())
    }

    @Test
    fun `the same seed gives the same order`() {
        assertEquals(draw(30, 7), draw(30, 7))
    }

    @Test
    fun `nothing to draw is empty`() {
        assertFalse(ShuffledIndices(0, Random(1)).hasNext())
    }

    @Test
    fun `taking five of a million indices holds five, not a million`() {
        val indices = ShuffledIndices(1_000_000, Random(3))
        val taken = List(5) { indices.next() }
        assertEquals(5, taken.toSet().size)
        assertTrue(taken.all { it in 0 until 1_000_000 })
        assertTrue("held ${indices.tracked}", indices.tracked <= 5)
    }
}
