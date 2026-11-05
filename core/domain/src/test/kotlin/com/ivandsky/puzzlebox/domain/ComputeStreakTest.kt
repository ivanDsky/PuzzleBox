package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cached-data-only streak teaser (A8): consecutive days ending at the newest capture. */
class ComputeStreakTest {

    private val d = { day: Int -> LocalDate.of(2026, 11, day) }

    private val compute = ComputeStreak()

    private fun streakEndingAtNewest(days: List<LocalDate>) = compute(days.map(::captured))

    private fun captured(day: LocalDate, archived: Boolean = false) =
        GameHistoryDay(GameId("zip"), day, puzzleNumber = null, record = null, archived = archived)

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

    @Test
    fun `an archived day neither extends nor bridges the run`() {
        val days = listOf(captured(d(4)), captured(d(5)), captured(d(6), archived = true), captured(d(3), archived = true))
        assertEquals(2, compute(days))
        assertEquals(1, compute(listOf(captured(d(2)), captured(d(3), archived = true), captured(d(4)))))
    }
}
