package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ladder's one attack relation: touching, sharing a unit, and their union. */
class QueensAttackTest {

    /** Region 1 holds cells 3 and 14, which share no row or column. */
    private val five = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 0, 1, 1,
            0, 2, 2, 2, 1,
            3, 2, 2, 2, 1,
            3, 3, 3, 4, 4,
            3, 3, 4, 4, 4,
        ),
    )

    private fun touching(cell: Int, size: Int): Set<Int> =
        (0 until size * size).filter { QueensAttack.touches(cell, it, size) }.toSet()

    @Test
    fun `touches is the 8 surrounding cells, never the cell itself`() {
        assertEquals(setOf(6, 7, 8, 11, 13, 16, 17, 18), touching(12, 5))
        assertFalse(QueensAttack.touches(12, 12, 5))
    }

    @Test
    fun `touches does not wrap around a board edge`() {
        assertEquals(setOf(3, 8, 9), touching(4, 5))
        assertFalse(QueensAttack.touches(4, 5, 5))
        assertFalse(QueensAttack.touches(9, 10, 5))
        assertEquals(setOf(1, 5, 6), touching(0, 5))
    }

    @Test
    fun `shares unit is the same row, column or region, never the cell itself`() {
        assertTrue(QueensAttack.sharesUnit(five, 10, 14))
        assertTrue(QueensAttack.sharesUnit(five, 2, 22))
        assertTrue(QueensAttack.sharesUnit(five, 3, 14))
        assertFalse(QueensAttack.sharesUnit(five, 0, 14))
        assertFalse(QueensAttack.sharesUnit(five, 3, 3))
    }

    @Test
    fun `attacks is touching or sharing a unit`() {
        val reach = (0 until 25).filter { QueensAttack.attacks(five, 12, it) }.toSet()
        assertEquals(setOf(10, 11, 13, 14, 2, 7, 17, 22, 6, 8, 16, 18), reach)
        assertTrue(QueensAttack.attacks(five, 0, 6))
        assertFalse(QueensAttack.attacks(five, 0, 13))
        assertFalse(QueensAttack.attacks(five, 0, 0))
    }

    /** The relation reads the board only; whether a shared unit is closed is the rung's call, by queens per unit. */
    @Test
    fun `on the N=2 board the relation is the same board geometry`() {
        val n8N2 = QueensTestSupport.n8N2Puzzle()
        assertEquals(setOf(0, 1, 2, 8, 10, 16, 17, 18), touching(9, 8))
        // two queens of one solution row: same unit, not touching
        assertTrue(QueensAttack.sharesUnit(n8N2, 1, 3))
        assertFalse(QueensAttack.touches(1, 3, 8))
        assertTrue(QueensAttack.attacks(n8N2, 1, 3))
        // region 1 only: rows 0 and 1, columns 7 and 4
        assertTrue(QueensAttack.sharesUnit(n8N2, 7, 12))
        // touching only: rows 1 and 2, columns 5 and 4, regions 1 and 2
        assertFalse(QueensAttack.sharesUnit(n8N2, 13, 20))
        assertTrue(QueensAttack.attacks(n8N2, 13, 20))
        assertFalse(QueensAttack.attacks(n8N2, 0, 63))
    }
}
