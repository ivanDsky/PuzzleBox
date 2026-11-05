package com.ivandsky.puzzlebox.history

import androidx.paging.testing.asSnapshot
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
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
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val firstDay: LocalDate = LocalDate.of(2026, 1, 1)

    /** [count] captured Zip days, newest first (the repository contract). */
    private fun seededRepo(count: Int): FakeDailyRepository = FakeDailyRepository().apply {
        setHistory(
            ZIP,
            (0 until count).map { offset ->
                HistoryTestSupport.entry(firstDay.plusDays((count - 1 - offset).toLong()))
            },
        )
    }

    private fun viewModel(daily: FakeDailyRepository): HistoryViewModel =
        HistoryTestSupport.viewModel(daily, FakeCompletionRepository())

    @Test
    fun `initial load is one lazy page, not the whole history`() = runTest {
        val items = viewModel(seededRepo(45)).pagedDays.asSnapshot()

        assertEquals(PAGE_SIZE, items.size)
        assertEquals(firstDay.plusDays(44), items.first().day)
    }

    @Test
    fun `scrolling reaches all 45 seeded days`() = runTest {
        val items = viewModel(seededRepo(45)).pagedDays.asSnapshot {
            appendScrollWhile { it.day > firstDay }
        }

        assertEquals(45, items.size)
        assertEquals(firstDay, items.last().day)
    }

    @Test
    fun `rows arrive newest first`() = runTest {
        val items = viewModel(seededRepo(PAGE_SIZE)).pagedDays.asSnapshot()

        assertEquals(
            (PAGE_SIZE - 1 downTo 0).map { firstDay.plusDays(it.toLong()) },
            items.map { it.day },
        )
    }

    @Test
    fun `a newly captured day invalidates to a fresh generation`() = runTest {
        val repo = seededRepo(45)
        val flow = viewModel(repo).pagedDays

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
        assertEquals(3, viewModel(seededRepo(3)).pagedDays.asSnapshot().size)
    }

    // ------------------------------------------------------------------
    // tango-05: the merged both-games list.
    // ------------------------------------------------------------------

    @Test
    fun `merged rows sort day first, capture recency second, game third`() = runTest {
        val daily = FakeDailyRepository().apply {
            setHistory(
                ZIP,
                listOf(
                    HistoryTestSupport.entry(LocalDate.parse("2026-10-31"), fetchedAtMs = 100L),
                    HistoryTestSupport.entry(LocalDate.parse("2026-10-30"), fetchedAtMs = 50L),
                ),
            )
            setHistory(
                TANGO,
                listOf(
                    HistoryTestSupport.tangoEntry(LocalDate.parse("2026-10-31"), fetchedAtMs = 200L),
                    HistoryTestSupport.tangoEntry(LocalDate.parse("2026-10-29"), fetchedAtMs = 10L),
                ),
            )
        }

        val rows = viewModel(daily).uiState.first { it.rows.isNotEmpty() }.rows

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
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(firstDay.plusDays(1))))
            setHistory(TANGO, listOf(HistoryTestSupport.tangoEntry(firstDay)))
        }

        val state = viewModel(daily).uiState.first { it.rows.isNotEmpty() }

        assertEquals(2, state.rows.size) // two games, two distinct days, no rows lost
        assertEquals(2, state.streak) // consecutive captured days
    }

    @Test
    fun `an archived solved day is listed but leaves the streak unchanged`() = runTest {
        val archivedDay = LocalDate.of(2026, 10, 31)
        val daily = FakeDailyRepository().apply {
            setHistory(
                ZIP,
                listOf(
                    HistoryTestSupport.entry(LocalDate.of(2026, 11, 2)),
                    HistoryTestSupport.entry(LocalDate.of(2026, 11, 1)),
                    HistoryTestSupport.entry(archivedDay).copy(archived = true),
                ),
            )
        }
        val completions = FakeCompletionRepository(listOf(HistoryTestSupport.record(archivedDay)))

        val state = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.isNotEmpty() }

        assertEquals(3, state.rows.size)
        assertEquals(HistoryTestSupport.record(archivedDay), state.recordsByKey[ZIP to archivedDay])
        assertEquals(2, state.streak)
    }

    @Test
    fun `a day from another year shows its year, a current-year day does not`() = runTest {
        val daily = FakeDailyRepository().apply {
            setHistory(
                ZIP,
                listOf(
                    HistoryTestSupport.entry(LocalDate.of(2026, 11, 1)),
                    HistoryTestSupport.entry(LocalDate.of(2024, 3, 4)).copy(archived = true),
                ),
            )
        }

        val rows = viewModel(daily).uiState.first { it.rows.isNotEmpty() }.rows

        assertEquals(listOf("Sunday, November 1", "Monday, March 4, 2024"), rows.map { it.headline })
    }

    @Test
    fun `browsing past days adds no history rows`() = runTest {
        val browsed = LocalDate.of(2025, 6, 1)
        val daily = seededRepo(2).apply {
            setArchiveRange(ZIP, LocalDate.of(2024, 1, 1)..LocalDate.of(2026, 11, 5))
            setDay(ZIP, browsed, DailyDay.NotCaptured)
        }
        val vm = viewModel(daily)
        val before = vm.uiState.first { it.rows.isNotEmpty() }.rows

        daily.archiveRange(ZIP)
        daily.day(ZIP, browsed)
        advanceUntilIdle()

        assertEquals(before, vm.uiState.value.rows)
    }
}
