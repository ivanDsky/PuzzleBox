package com.ivandsky.puzzlebox.history

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cached-data-only streak teaser (A8): consecutive days ending at the newest capture. */
class StreakTest {

    private val d = { day: Int -> LocalDate.of(2026, 11, day) }

    @Test
    fun `no captures is no streak`() {
        assertEquals(0, streakEndingAtNewest(emptyList()))
    }

    @Test
    fun `one capture is a one-day streak`() {
        assertEquals(1, streakEndingAtNewest(listOf(d(3))))
    }

    @Test
    fun `consecutive run ending at newest counts fully`() {
        assertEquals(5, streakEndingAtNewest(listOf(d(1), d(2), d(3), d(4), d(5))))
    }

    @Test
    fun `a gap breaks the run at the gap`() {
        assertEquals(3, streakEndingAtNewest(listOf(d(1), d(2), d(3), d(5), d(6), d(7))))
    }

    @Test
    fun `older run does not survive past the break`() {
        assertEquals(1, streakEndingAtNewest(listOf(d(1), d(2), d(8))))
    }

    @Test
    fun `input order does not matter`() {
        assertEquals(3, streakEndingAtNewest(listOf(d(3), d(1), d(2))))
    }

    @Test
    fun `duplicates count once`() {
        assertEquals(2, streakEndingAtNewest(listOf(d(3), d(3), d(4), d(4))))
    }
}
