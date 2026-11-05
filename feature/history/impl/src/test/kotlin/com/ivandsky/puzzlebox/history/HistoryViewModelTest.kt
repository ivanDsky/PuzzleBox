package com.ivandsky.puzzlebox.history

import androidx.paging.testing.asSnapshot
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

        val state = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.isNotEmpty() && it.stats != null }

        assertEquals(3, state.rows.size) // two games, three distinct days, no rows lost
        assertEquals(2, state.stats?.currentStreak) // solved today + yesterday; the opened day never counts
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

        val state = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.isNotEmpty() && it.stats != null }

        assertNotNull("the practice solve is still a solved row", state.rows.single { it.gameId == TANGO }.record)
        assertEquals(1, state.stats?.currentStreak) // today only; yesterday's practice solve doesn't bridge
    }

    // ------------------------------------------------------------------
    // S06: tabs, best, weeks.
    // ------------------------------------------------------------------

    @Test
    fun `the key picks the starting tab, and no game or an unknown game means All`() {
        val daily = FakeDailyRepository()
        fun tabOf(key: HistoryKey) = HistoryTestSupport.viewModel(daily, FakeCompletionRepository(), key).selectedTab.value

        assertEquals(QUEENS, tabOf(HistoryKey(QUEENS.value)))
        assertNull(tabOf(HistoryKey()))
        assertNull(tabOf(HistoryKey("nope")))
    }

    @Test
    fun `a game tab lists only that game, in the state and the pages`() = runTest {
        val today = HistoryTestSupport.TODAY
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(today.minusDays(1))))
            setHistory(TANGO, listOf(HistoryTestSupport.tangoEntry(today)))
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository())

        viewModel.select(TANGO)

        assertEquals(TANGO, viewModel.selectedTab.value)
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }
        assertEquals(listOf(TANGO), state.rows.map { it.gameId })
        assertEquals(1, state.dotsPerDay)
        assertEquals(listOf(TANGO), viewModel.pagedDays.asSnapshot().map { it.gameId })

        viewModel.select(null)
        advanceUntilIdle()

        assertEquals(3, viewModel.pagedDays.asSnapshot().size)
    }

    @Test
    fun `only each game's fastest numbered solve is its best`() = runTest {
        val today = HistoryTestSupport.TODAY
        val practiceDay = today.minusDays(3)
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(today.minusDays(1))))
            setHistory(TANGO, listOf(HistoryTestSupport.tangoEntry(today), HistoryTestSupport.tangoEntry(practiceDay, puzzleNumber = null)))
        }
        val completions = FakeCompletionRepository().apply {
            setRecords(
                listOf(
                    HistoryTestSupport.record(today, timeMs = 95_000),
                    HistoryTestSupport.record(today.minusDays(1), timeMs = 70_000),
                    HistoryTestSupport.tangoRecord(today, timeMs = 61_000),
                    // Faster, but practice: never a best.
                    HistoryTestSupport.tangoRecord(practiceDay, timeMs = 5_000)
                        .copy(puzzleRef = TestGames.Ref(TANGO, practiceDay, null)),
                ),
            )
        }

        val rows = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.size == 4 }.rows

        assertEquals(setOf("zip/${today.minusDays(1)}", "tango/$today"), rows.filter { it.isBest }.map { it.key }.toSet())
    }

    @Test
    fun `the weeks run Monday to Sunday, newest first, with practice left out`() {
        val today = HistoryTestSupport.TODAY // a Tuesday
        val rows = listOf(
            HistoryDayRow(today, 1, ZIP, "Zip", record = HistoryTestSupport.record(today)),
            HistoryDayRow(today.minusDays(1), null, TANGO, "Tango", record = HistoryTestSupport.tangoRecord(today.minusDays(1))),
            HistoryDayRow(today.minusDays(2), 2, ZIP, "Zip"),
        )

        val weeks = historyWeeks(today, rows, listOf(ZIP, TANGO))

        assertEquals(HISTORY_WEEKS, weeks.size)
        assertTrue(weeks.all { week -> week.size == 7 && week.first().date.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue("newest week first", weeks[0].first().date > weeks[1].first().date)
        assertTrue("this week holds today", weeks[0].any { it.date == today })
        // today - 2 is Sunday: the end of last week.
        val shown = weeks.flatten().associateBy { it.date }
        assertEquals(1, shown.getValue(today).solved.size)
        assertTrue(shown.getValue(today).isToday)
        assertTrue("practice fills no dot", shown.getValue(today.minusDays(1)).solved.isEmpty())
        assertTrue("an unfinished day fills no dot", shown.getValue(today.minusDays(2)).solved.isEmpty())
    }

    @Test
    fun `the history edition switches on the edition number`() {
        val day = HistoryTestSupport.TODAY
        fun row(puzzleNumber: Int?) = HistoryDayRow(day = day, puzzleNumber = puzzleNumber, gameId = TANGO, gameName = "Tango")
        assertNotEquals(row(puzzleNumber = 5).edition, row(puzzleNumber = null).edition)
        assertTrue("a practice day never claims an edition", "#" !in row(puzzleNumber = null).title)
    }
}
