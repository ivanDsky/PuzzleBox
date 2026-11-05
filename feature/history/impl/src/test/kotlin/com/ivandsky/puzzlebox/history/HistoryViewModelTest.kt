package com.ivandsky.puzzlebox.history

import androidx.paging.testing.asSnapshot
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `merged rows keep both games on the same day and the streak sees solved days only`() = runTest {
        val today = HistoryTestSupport.TODAY
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(today.minusDays(2))))
            setHistory(TANGO, listOf(HistoryTestSupport.tangoEntry(today.minusDays(1))))
        }
        val completions = FakeCompletionRepository().apply {
            // Today (zip) and yesterday (tango) solved; two days ago only opened.
            setRecords(listOf(HistoryTestSupport.record(today), HistoryTestSupport.tangoRecord(today.minusDays(1))))
        }

        val state = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.isNotEmpty() }

        assertEquals(3, state.rows.size) // two games, three distinct days, no rows lost
        assertEquals(2, state.streak) // solved today + yesterday; the opened day never counts
    }

    @Test
    fun `a practice solve (no edition number) lists but never counts toward the streak`() = runTest {
        val today = HistoryTestSupport.TODAY
        val yesterday = today.minusDays(1)
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today)))
            setHistory(TANGO, listOf(HistoryTestSupport.tangoEntry(yesterday, puzzleNumber = null)))
        }
        val completions = FakeCompletionRepository().apply {
            val practice = HistoryTestSupport.tangoRecord(yesterday).copy(puzzleRef = TestGames.Ref(TANGO, yesterday, null))
            setRecords(listOf(HistoryTestSupport.record(today), practice))
        }

        val state = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.isNotEmpty() }

        assertTrue("the practice solve is still a solved row", (TANGO to yesterday) in state.recordsByKey)
        assertEquals(1, state.streak) // today only; yesterday's practice solve doesn't bridge
    }

    @Test
    fun `the key picks the game tab, and no game means every game`() {
        val daily = FakeDailyRepository()
        assertEquals(ZIP, HistoryTestSupport.viewModel(daily, FakeCompletionRepository(), HistoryKey(ZIP.value)).game)
        assertNull(HistoryTestSupport.viewModel(daily, FakeCompletionRepository()).game)
    }

    @Test
    fun `the history label switches on the edition number`() {
        val day = HistoryTestSupport.TODAY
        fun row(puzzleNumber: Int?) = HistoryDayRow(day = day, puzzleNumber = puzzleNumber, gameId = TANGO, gameName = "Tango")
        assertNotEquals(row(puzzleNumber = 5).label, row(puzzleNumber = null).label)
        assertTrue("a practice label never claims an edition", "#" !in row(puzzleNumber = null).label)
    }
}
