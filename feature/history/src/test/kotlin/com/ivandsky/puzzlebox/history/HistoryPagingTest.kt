package com.ivandsky.puzzlebox.history

import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val items = historyDays(seededRepo(45)).asSnapshot()

        assertEquals(PAGE_SIZE, items.size)
        assertEquals(firstDay.plusDays(44), items.first().day)
    }

    @Test
    fun `scrolling reaches all 45 seeded days`() = runTest {
        val items = historyDays(seededRepo(45)).asSnapshot {
            appendScrollWhile { it.day > firstDay }
        }

        assertEquals(45, items.size)
        assertEquals(firstDay, items.last().day)
    }

    @Test
    fun `rows arrive newest first`() = runTest {
        val items = historyDays(seededRepo(PAGE_SIZE)).asSnapshot()

        assertEquals(
            (PAGE_SIZE - 1 downTo 0).map { firstDay.plusDays(it.toLong()) },
            items.map { it.day },
        )
    }

    @Test
    fun `a newly captured day invalidates to a fresh generation`() = runTest {
        val repo = seededRepo(45)
        val flow = historyDays(repo)

        assertEquals(45, flow.asSnapshot { appendScrollWhile { true } }.size)

        val brandNewDay = firstDay.plusDays(45)
        repo.addEntry(HistoryTestSupport.entry(brandNewDay))
        advanceUntilIdle()

        val refreshed = flow.asSnapshot { appendScrollWhile { true } }
        assertEquals(46, refreshed.size)
        assertEquals(brandNewDay, refreshed.first().day)
    }

    @Test
    fun `anchored refresh refills backward without duplicate keys`() = runTest {
        // Jump the anchor deep into the list: the refresh lands its forward page, then
        // everything before it must come from PREPEND windows (the duplicate-row
        // regression). How far the final prepend drains is prefetch-dependent by
        // design — the stable contract is: unique keys, contiguous days, newest-first.
        val refreshed = historyDays(seededRepo(45)).asSnapshot {
            scrollTo(30)
            prependScrollWhile { true }
        }
        val days = refreshed.map { it.day }
        assertTrue("never fewer than the anchor page", refreshed.size >= PAGE_SIZE)
        assertEquals("every row must have a unique day key", refreshed.size, days.distinct().size)
        assertEquals("loaded windows must tile contiguously, newest first", days.sortedDescending(), days)
        assertEquals(firstDay.plusDays(44), days.first())
        assertTrue("anchor row (index 30) must be loaded", days.contains(firstDay.plusDays(14)))
    }

    @Test
    fun `first generation is seeded, never an empty flash`() = runTest {
        // No settle step before the first collection: whatever generation arrives
        // first must already carry the seeded rows (the Pager is only ever built
        // from mapped rows, so an empty-list generation cannot exist).
        assertEquals(3, historyDays(seededRepo(3)).asSnapshot().size)
    }
}

/** Direct source contracts for the prepend windows the pager-level tests infer. */
class HistoryPagingSourceTest {

    private val firstDay: LocalDate = LocalDate.of(2026, 1, 1)

    private fun source(count: Int): HistoryPagingSource = HistoryPagingSource(
        (0 until count).map { offset ->
            HistoryTestSupport.entry(firstDay.plusDays((count - 1 - offset).toLong()))
        }.map { it.toRow() },
    )

    private suspend fun prepend(source: HistoryPagingSource, key: Int): PagingSource.LoadResult.Page<Int, HistoryDayRow> {
        val params: PagingSource.LoadParams<Int> =
            PagingSource.LoadParams.Prepend(key, PAGE_SIZE, placeholdersEnabled = false)
        return source.load(params) as PagingSource.LoadResult.Page
    }

    @Test
    fun `prepend is the backward window ending at the key`() = runTest {
        val page = prepend(source(45), key = 25)

        // [5, 25): the 20 rows BEFORE index 25 — never the forward [25, 45) re-delivery.
        assertEquals(
            (39 downTo 20).map { firstDay.plusDays(it.toLong()) },
            page.data.map { it.day },
        )
        assertEquals(5, page.prevKey)
        assertEquals(25, page.nextKey)
        assertEquals(5, page.itemsBefore)
        assertEquals(20, page.itemsAfter)
    }

    @Test
    fun `prepend clamps at the start and drops prevKey`() = runTest {
        val page = prepend(source(45), key = 10)

        // [0, 10): the ten newest-adjacent rows at the start of the list.
        assertEquals(10, page.data.size)
        assertEquals(firstDay.plusDays(44), page.data.first().day)
        assertNull(page.prevKey)
        assertEquals(10, page.nextKey)
        assertEquals(0, page.itemsBefore)
        assertEquals(35, page.itemsAfter)
    }

    @Test
    fun `null-key prepend is rejected by the params contract`() {
        // Prepend's constructor enforces non-null keys (the pager only prepends when
        // prevKey != null), so load()'s null-key empty-page branch is a soundness net
        // for the nullable property — unreachable through the public API. Assert the
        // contract seam that makes it so.
        val ctor = PagingSource.LoadParams.Prepend::class.java.constructors.first { it.parameterCount == 3 }
        val outcome = runCatching { ctor.newInstance(null, PAGE_SIZE, false) }
        assertTrue("Prepend must reject null keys", outcome.isFailure)
    }
}
