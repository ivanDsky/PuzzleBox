package com.ivandsky.puzzlebox.play

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M10-5: which day a past day's Result continues on. */
class ArchiveNextDayTest {

    private val d = LocalDate.of(2026, 11, 10)
    private fun day(offset: Long) = d.plusDays(offset)
    private val open = day(-10)..day(5)
    private val yesterday = day(4)

    private fun pick(solved: Set<LocalDate> = emptySet(), from: LocalDate = day(0), range: ClosedRange<LocalDate> = open) =
        nextArchiveDay(solved + from, range, from, yesterday)

    @Test
    fun `the next day after the solved one, when it is unsolved`() {
        assertEquals(day(1), pick())
    }

    @Test
    fun `solved days after it are skipped`() {
        assertEquals(day(3), pick(solved = setOf(day(1), day(2))))
    }

    @Test
    fun `it goes up to yesterday and no further`() {
        assertEquals(day(4), pick(from = day(3)))
        assertEquals(day(3), pick(from = day(4), solved = setOf(day(5))))
    }

    @Test
    fun `with every later day solved it wraps to the nearest unsolved day before`() {
        assertEquals(day(-2), pick(solved = (1L..4L).map(::day).toSet() + day(-1)))
    }

    @Test
    fun `with nothing unsolved anywhere there is no next day`() {
        val all = (-10L..4L).map(::day).toSet()
        assertNull(pick(solved = all))
    }

    @Test
    fun `days the game has no puzzle for are skipped`() {
        val narrow = day(-1)..day(2)
        assertEquals(day(2), pick(solved = setOf(day(1)), range = narrow))
        assertEquals(day(-1), pick(solved = setOf(day(1), day(2)), range = narrow))
        assertNull(pick(solved = setOf(day(-1), day(1), day(2)), range = narrow))
    }

    @Test
    fun `a solved day outside the archive has nothing to continue`() {
        assertNull(nextArchiveDay(emptySet(), open, day(8), yesterday))
        assertNull(nextArchiveDay(emptySet(), open, day(-11), yesterday))
    }

    @Test
    fun `yesterday before the first day leaves only days before`() {
        assertEquals(day(-1), nextArchiveDay(setOf(day(0)), open, day(0), yesterday = day(0)))
    }
}
