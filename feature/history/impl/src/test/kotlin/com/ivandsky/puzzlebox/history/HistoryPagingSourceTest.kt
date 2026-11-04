package com.ivandsky.puzzlebox.history

import androidx.paging.PagingSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        }.map { entry -> HistoryDayRow(entry.day, entry.puzzleNumber, HistoryTestSupport.ZIP, "Zip") },
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
