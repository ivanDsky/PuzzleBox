package com.ivandsky.puzzlebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreezeCountRuleTest {
    private fun holding(held: Int) = StreakFreezes(held = held, frozenDays = emptySet(), daysToNext = 3, lastSolve = null)

    @Test
    fun `a streak of 0 hides the count however many are held - F5`() {
        assertNull(holding(3).heldWhileStreak(streak = 0))
    }

    @Test
    fun `a live streak with none held shows 0`() {
        assertEquals(0, holding(0).heldWhileStreak(streak = 1))
    }

    @Test
    fun `a live streak shows the held count`() {
        assertEquals(2, holding(2).heldWhileStreak(streak = 5))
    }
}
