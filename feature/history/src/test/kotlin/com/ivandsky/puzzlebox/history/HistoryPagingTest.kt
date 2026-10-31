package com.ivandsky.puzzlebox.history

import androidx.paging.testing.asSnapshot
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DoD: paging works past 30 days over seeded data (A8 "paginated lazily"). 45 seeded
 * days against a 20-row page: the initial load must stay lazy, scrolling must reach
 * the whole history, and a newly captured day must invalidate to a fresh generation.
 * (Mid-way counts are prefetch-dependent by design, so only the two stable states —
 * untouched and fully scrolled — are asserted.)
 */
class HistoryPagingTest {

    private val firstDay: LocalDate = LocalDate.of(2026, 1, 1)

    /** [count] captured days, newest first (the repository contract). */
    private fun seededRepo(count: Int): FakeZipDailyRepository =
        FakeZipDailyRepository(
            history = (0 until count).map { offset ->
                HistoryTestSupport.entry(firstDay.plusDays((count - 1 - offset).toLong()))
            },
        )

    @Test
    fun `initial load is one lazy page, not the whole history`() = runTest {
        val items = historyDays(seededRepo(45), backgroundScope).asSnapshot()

        assertEquals(PAGE_SIZE, items.size)
        assertEquals(firstDay.plusDays(44), items.first().day)
    }

    @Test
    fun `scrolling reaches all 45 seeded days`() = runTest {
        val items = historyDays(seededRepo(45), backgroundScope).asSnapshot {
            appendScrollWhile { it.day > firstDay }
        }

        assertEquals(45, items.size)
        assertEquals(firstDay, items.last().day)
    }

    @Test
    fun `rows arrive newest first`() = runTest {
        val items = historyDays(seededRepo(PAGE_SIZE), backgroundScope).asSnapshot()

        assertEquals(
            (PAGE_SIZE - 1 downTo 0).map { firstDay.plusDays(it.toLong()) },
            items.map { it.day },
        )
    }

    @Test
    fun `a newly captured day invalidates to a fresh generation`() = runTest {
        val repo = seededRepo(45)
        val flow = historyDays(repo, backgroundScope)

        assertEquals(45, flow.asSnapshot { appendScrollWhile { true } }.size)

        val brandNewDay = firstDay.plusDays(45)
        repo.addEntry(HistoryTestSupport.entry(brandNewDay))
        advanceUntilIdle()

        val refreshed = flow.asSnapshot { appendScrollWhile { true } }
        assertEquals(46, refreshed.size)
        assertEquals(brandNewDay, refreshed.first().day)
    }
}
