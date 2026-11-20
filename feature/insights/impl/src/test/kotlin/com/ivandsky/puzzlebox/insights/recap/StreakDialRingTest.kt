package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.default
import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.longest
import com.ivandsky.puzzlebox.insights.recap.StreakFixtures.year
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ring the dial draws: which ticks the streak lights, which are ice, which are still to come. */
class StreakDialRingTest {

    private val afterOctober = LocalDate.of(2026, 11, 2)
    private val december20 = LocalDate.of(2026, 12, 20)

    @Test
    fun `a month draws a tick per day and lights only the streak's own, the frozen ones as ice`() {
        val ring = dialRing(default, afterOctober)

        assertEquals(31, ring.ticks.size)
        assertEquals(23, ring.ticks.count { it.fill > 0f })
        assertEquals(5, ring.first)
        assertEquals(23, ring.count)
        assertEquals(listOf(13, 20), ring.ticks.indices.filter { ring.ticks[it].frozen })
    }

    @Test
    fun `a streak that runs the whole month lights every tick`() {
        val ring = dialRing(longest, afterOctober)

        assertEquals(31, ring.ticks.count { it.fill == 1f })
        assertEquals(4, ring.ticks.count { it.frozen })
    }

    @Test
    fun `a year draws 53 week ticks, each filled by the share of its days the streak ran`() {
        val ring = dialRing(year, december20)

        assertEquals(53, ring.ticks.size)
        // Jun 24 is a Wednesday and Sep 5 a Saturday: the first and last weeks are filled in part.
        assertEquals(25, ring.first)
        assertEquals(11, ring.count)
        assertEquals(5f / 7f, ring.ticks[25].fill, 0.001f)
        assertEquals(1f, ring.ticks[30].fill, 0f)
        assertEquals(6f / 7f, ring.ticks[35].fill, 0.001f)
        assertEquals(0f, ring.ticks[24].fill, 0f)
        assertEquals(0f, ring.ticks[36].fill, 0f)
    }

    @Test
    fun `a year under way draws the weeks still to come faint, and a finished year none`() {
        assertEquals(listOf(51, 52), dialRing(year, december20).ticks.indices.filter { dialRing(year, december20).ticks[it].future })
        assertEquals(0, dialRing(year, LocalDate.of(2027, 1, 5)).ticks.count { it.future })
    }

    @Test
    fun `a year's frozen days mark the week they fall in`() {
        val ring = dialRing(year, december20)

        // Jul 1, Jul 15 and Aug 5 sit in weeks 26, 28 and 31.
        assertEquals(listOf(26, 28, 31), ring.ticks.indices.filter { ring.ticks[it].frozen })
    }
}
