package com.ivandsky.puzzlebox.history

import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
 *
 * tango-05: the pager pages the MERGED both-games row list — the merge order (day
 * desc, capture recency, game tiebreak) is covered in the dedicated merge tests.
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
        val items = historyDays(seededRepo(45), FakeTangoDailyRepository(), FakeCompletionRepository(), emptyMap()).asSnapshot()

        assertEquals(PAGE_SIZE, items.size)
        assertEquals(firstDay.plusDays(44), items.first().day)
    }

    @Test
    fun `scrolling reaches all 45 seeded days`() = runTest {
        val items = historyDays(seededRepo(45), FakeTangoDailyRepository(), FakeCompletionRepository(), emptyMap()).asSnapshot {
            appendScrollWhile { it.day > firstDay }
        }

        assertEquals(45, items.size)
        assertEquals(firstDay, items.last().day)
    }

    @Test
    fun `rows arrive newest first`() = runTest {
        val items = historyDays(seededRepo(PAGE_SIZE), FakeTangoDailyRepository(), FakeCompletionRepository(), emptyMap()).asSnapshot()

        assertEquals(
            (PAGE_SIZE - 1 downTo 0).map { firstDay.plusDays(it.toLong()) },
            items.map { it.day },
        )
    }

    @Test
    fun `a newly captured day invalidates to a fresh generation`() = runTest {
        val repo = seededRepo(45)
        val flow = historyDays(repo, FakeTangoDailyRepository(), FakeCompletionRepository(), emptyMap())

        assertEquals(45, flow.asSnapshot { appendScrollWhile { true } }.size)

        val brandNewDay = firstDay.plusDays(45)
        repo.addEntry(HistoryTestSupport.entry(brandNewDay))
        advanceUntilIdle()

        val refreshed = flow.asSnapshot { appendScrollWhile { true } }
        assertEquals(46, refreshed.size)
        assertEquals(brandNewDay, refreshed.first().day)
    }

    @Test
    fun `first generation is seeded, never an empty flash`() = runTest {
        // No settle step before the first collection: whatever generation arrives
        // first must already carry the seeded rows (the Pager is only ever built
        // from mapped rows, so an empty-list generation cannot exist).
        assertEquals(3, historyDays(seededRepo(3), FakeTangoDailyRepository(), FakeCompletionRepository(), emptyMap()).asSnapshot().size)
    }

    // ------------------------------------------------------------------
    // tango-05: the merged both-games list.
    // ------------------------------------------------------------------

    @Test
    fun `merged rows sort day first, capture recency second, game third`() = runTest {
        val zips = FakeZipDailyRepository(
            history = listOf(
                HistoryTestSupport.entry(LocalDate.parse("2026-10-31"), fetchedAtMs = 100L),
                HistoryTestSupport.entry(LocalDate.parse("2026-10-30"), fetchedAtMs = 50L),
            ),
        )
        val tangos = FakeTangoDailyRepository(
            history = listOf(
                HistoryTestSupport.tangoEntry(LocalDate.parse("2026-10-31"), fetchedAtMs = 200L),
                HistoryTestSupport.tangoEntry(LocalDate.parse("2026-10-29"), fetchedAtMs = 10L),
            ),
        )

        val rows = historyRows(zips, tangos, FakeCompletionRepository(), emptyMap()).first()

        // Day desc: 10-31 (both games, recency: tango 200 > zip 100), then 10-30, then 10-29.
        assertEquals(
            listOf(
                "tango/2026-10-31",
                "zip/2026-10-31",
                "zip/2026-10-30",
                "tango/2026-10-29",
            ),
            rows.map { it.key },
        )
    }

    @Test
    fun `merged rows keep both games on the same day and the streak sees the union`() = runTest {
        val zips = FakeZipDailyRepository(history = listOf(HistoryTestSupport.entry(firstDay.plusDays(1))))
        val tangos = FakeTangoDailyRepository(history = listOf(HistoryTestSupport.tangoEntry(firstDay)))

        val rows = historyRows(zips, tangos, FakeCompletionRepository(), emptyMap()).first()

        assertEquals(2, rows.size) // two games, two distinct days, no rows lost
        assertEquals(2, streakEndingAtNewest(rows.map { it.day })) // consecutive captured days
    }
}

/**
 * The Prepend branch's real coverage lives HERE: [HistoryPagingSourceTest] drives
 * `load(Prepend)` directly and fails against a forward-window regression (verified —
 * both window tests fail against the buggy source). The pager-level asSnapshot API
 * cannot exercise a genuine Prepend: scrollTo/flingTo are append-served (a full 45-row
 * fling drain passes even against a forward-window Prepend), and a mid-script
 * generation switch restarts the presenter unanchored (leading prevKey = null), so
 * prepend is never reached there.
 */
class HistoryPagingSourceTest {

    private val firstDay: LocalDate = LocalDate.of(2026, 1, 1)

    private fun source(count: Int): HistoryPagingSource = HistoryPagingSource(
        (0 until count).map { offset ->
            HistoryTestSupport.entry(firstDay.plusDays((count - 1 - offset).toLong()))
        }.map { entry -> HistoryDayRow(entry.day, entry.puzzleNumber, ZipDefinition.id, "Zip") },
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
