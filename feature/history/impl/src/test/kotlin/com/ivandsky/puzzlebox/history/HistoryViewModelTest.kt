package com.ivandsky.puzzlebox.history

import androidx.paging.testing.asSnapshot
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            val practice = HistoryTestSupport.tangoRecord(yesterday).copy(puzzleRef = TestGames.Ref(TANGO, PuzzleKey.Daily(yesterday, null)))
            setRecords(listOf(HistoryTestSupport.record(today), practice))
        }

        val state = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.isNotEmpty() && it.stats != null }

        assertNotNull("the practice solve is still a solved row", state.rows.single { it.gameId == TANGO }.record)
        assertEquals(1, state.stats?.currentStreak) // today only; yesterday's practice solve doesn't bridge
    }

    // ------------------------------------------------------------------
    // S06: the All timeline, best, weeks.
    // ------------------------------------------------------------------

    @Test
    fun `the state and the pages hold every game's rows, one ring segment per game`() = runTest {
        val today = HistoryTestSupport.TODAY
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(today.minusDays(1))))
            setHistory(TANGO, listOf(HistoryTestSupport.tangoEntry(today)))
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository())

        val state = viewModel.uiState.first { it.rows.isNotEmpty() }
        assertEquals(setOf(ZIP, TANGO), state.rows.map { it.gameId }.toSet())
        assertEquals(listOf(ZIP, TANGO, QUEENS).size, state.gamesPerDay)
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
                        .copy(puzzleRef = TestGames.Ref(TANGO, PuzzleKey.Daily(practiceDay, null))),
                ),
            )
        }

        val rows = HistoryTestSupport.viewModel(daily, completions).uiState.first { it.rows.size == 4 }.rows

        assertEquals(setOf("zip/${today.minusDays(1)}", "tango/$today"), rows.filter { it.isBest }.map { it.key }.toSet())
    }

    @Test
    fun `a hints-ranked game's best is its fewest-hints numbered solve`() = runTest {
        val today = HistoryTestSupport.TODAY
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(today.minusDays(1))))
        }
        val completions = FakeCompletionRepository().apply {
            setRecords(
                listOf(
                    HistoryTestSupport.record(today, timeMs = 60_000, hints = 2),
                    // Slower, but fewer hints: hints rank first.
                    HistoryTestSupport.record(today.minusDays(1), timeMs = 90_000, hints = 0),
                ),
            )
        }

        val rows = HistoryTestSupport.viewModel(daily, completions, hintsRanked = setOf(ZIP))
            .uiState.first { it.rows.size == 2 }.rows

        assertEquals(listOf("zip/${today.minusDays(1)}"), rows.filter { it.isBest }.map { it.key })
        assertEquals(setOf(RankOrder.HintsThenTime), rows.map { it.rankOrder }.toSet())
    }

    @Test
    fun `calendar rings leave out practice and unfinished days, in registry order`() {
        val today = HistoryTestSupport.TODAY
        val zip = GameBrand.Neutral.copy(accent = AccentSlot.Blue)
        val tango = GameBrand.Neutral.copy(accent = AccentSlot.Orange)
        val rows = listOf(
            HistoryDayRow(today, 1, TANGO, "Tango", brand = tango, record = HistoryTestSupport.tangoRecord(today)),
            HistoryDayRow(today, 1, ZIP, "Zip", brand = zip, record = HistoryTestSupport.record(today)),
            HistoryDayRow(today.minusDays(1), null, TANGO, "Tango", brand = tango, record = HistoryTestSupport.tangoRecord(today.minusDays(1))),
            HistoryDayRow(today.minusDays(2), 2, ZIP, "Zip", brand = zip),
        )

        val solved = solvedByDay(rows, listOf(ZIP, TANGO))

        assertEquals(setOf(today), solved.keys)
        assertEquals(listOf(zip, tango), solved.getValue(today))
    }

    @Test
    fun `a month grid is whole Monday-first weeks around the month`() {
        val month = YearMonth.of(2026, 11) // starts on a Sunday, ends on a Monday

        val weeks = monthGrid(month)

        assertTrue(weeks.all { week -> week.size == 7 && week.first().dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(month.atDay(1), weeks.first().last())
        assertEquals(month.atEndOfMonth(), weeks.last().first())
        assertEquals(month.lengthOfMonth(), weeks.flatten().count { YearMonth.from(it) == month })
    }

    @Test
    fun `the calendar opens on this week and stays between the oldest row and today`() = runTest {
        val today = HistoryTestSupport.TODAY
        val oldest = LocalDate.of(2026, 9, 15)
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(oldest)))
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository())

        val opened = viewModel.uiState.first { it.rows.isNotEmpty() }
        assertEquals(today, opened.calendarDay)
        assertFalse("one week by default", opened.calendarExpanded)
        assertEquals(oldest, opened.earliestDay)

        val october = LocalDate.of(2026, 10, 1)
        viewModel.showCalendar(october, expanded = true)
        val expanded = viewModel.uiState.first { it.calendarExpanded }
        assertEquals(october, expanded.calendarDay)

        viewModel.showCalendar(LocalDate.of(2020, 1, 6), expanded = false)
        assertEquals(oldest, viewModel.uiState.first { !it.calendarExpanded }.calendarDay)

        viewModel.showCalendar(today.plusWeeks(3), expanded = false)
        assertEquals(today, viewModel.uiState.first { it.calendarDay != oldest }.calendarDay)
    }

    @Test
    fun `folding the month back picks the picked day, then this week, then the first play, then the 1st`() {
        val today = HistoryTestSupport.TODAY // Tuesday, November 3, 2026
        val november = YearMonth.from(today)
        val october = YearMonth.of(2026, 10)
        val played = setOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 22))

        assertEquals(
            LocalDate.of(2026, 10, 19),
            collapsedWeek(october, selectedDay = LocalDate.of(2026, 10, 22), today = today, daysWithRows = played),
        )
        assertEquals(
            LocalDate.of(2026, 11, 2),
            collapsedWeek(november, selectedDay = LocalDate.of(2026, 10, 22), today = today, daysWithRows = played),
        )
        assertEquals(LocalDate.of(2026, 10, 12), collapsedWeek(october, null, today, played))
        assertEquals(LocalDate.of(2026, 8, 31), collapsedWeek(YearMonth.of(2026, 9), null, today, played))
    }

    @Test
    fun `a week's title names its month, or both when it spans two`() {
        assertTrue(weekTitle(LocalDate.of(2026, 11, 2)).contains("2026"))
        assertEquals(weekTitle(LocalDate.of(2026, 11, 2)), weekTitle(LocalDate.of(2026, 11, 9)))
        assertTrue("a week across two years names both", weekTitle(LocalDate.of(2026, 12, 28)).contains("2027"))
        assertTrue(weekTitle(LocalDate.of(2026, 12, 28)).contains("2026"))
    }

    private fun openedAt(key: HistoryKey, vararg days: LocalDate): HistoryViewModel {
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, days.map { HistoryTestSupport.entry(it) })
        }
        return HistoryTestSupport.viewModel(daily, FakeCompletionRepository(), opening = key)
    }

    @Test
    fun `a game's key opens on its day with that game's row named`() = runTest {
        val day = HistoryTestSupport.TODAY.minusDays(2)
        val state = openedAt(HistoryKey(ZIP.value, day.toString()), day, HistoryTestSupport.TODAY)
            .uiState.first { it.rows.isNotEmpty() }
        assertEquals(day, state.selectedDay)
        assertEquals(day, state.calendarDay)
        assertEquals("${ZIP.value}/$day", state.openingRowKey)
    }

    @Test
    fun `a game's key without a day opens on today`() = runTest {
        val today = HistoryTestSupport.TODAY
        val state = openedAt(HistoryKey(ZIP.value), today).uiState.first { it.rows.isNotEmpty() }
        assertEquals(today, state.selectedDay)
        assertEquals("${ZIP.value}/$today", state.openingRowKey)
    }

    @Test
    fun `a game's key for a day with no row filters to it but names no row`() = runTest {
        val day = HistoryTestSupport.TODAY.minusDays(3)
        val state = openedAt(HistoryKey(ZIP.value, day.toString()), HistoryTestSupport.TODAY)
            .uiState.first { it.rows.isNotEmpty() }
        assertEquals(day, state.selectedDay)
        assertNull(state.openingRowKey)
    }

    @Test
    fun `a key without a game opens unfiltered`() = runTest {
        val state = openedAt(HistoryKey(), HistoryTestSupport.TODAY).uiState.first { it.rows.isNotEmpty() }
        assertNull(state.selectedDay)
        assertNull(state.openingRowKey)
    }

    @Test
    fun `a picked day toggles and ignores later days`() = runTest {
        val today = HistoryTestSupport.TODAY
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, listOf(HistoryTestSupport.entry(today.minusDays(1))))
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository())
        suspend fun picked() = viewModel.uiState.first { it.rows.isNotEmpty() || it.selectedDay != null }.selectedDay

        viewModel.selectDay(today.plusDays(1))
        advanceUntilIdle()
        assertNull(picked())

        viewModel.selectDay(today.minusDays(1))
        assertEquals(today.minusDays(1), viewModel.uiState.first { it.selectedDay != null }.selectedDay)

        viewModel.selectDay(today.minusDays(2))
        assertEquals("another day replaces the pick", today.minusDays(2), viewModel.uiState.first { it.selectedDay != today.minusDays(1) }.selectedDay)

        viewModel.selectDay(today.minusDays(2))
        assertNull("the same day again clears it", viewModel.uiState.first { it.selectedDay == null }.selectedDay)
    }

    @Test
    fun `the history edition switches on the edition number`() {
        val day = HistoryTestSupport.TODAY
        fun row(puzzleNumber: Int?) = HistoryDayRow(day = day, puzzleNumber = puzzleNumber, gameId = TANGO, gameName = "Tango")
        assertNotEquals(row(puzzleNumber = 5).edition, row(puzzleNumber = null).edition)
        assertTrue("a practice day never claims an edition", "#" !in row(puzzleNumber = null).title)
    }

    @Test
    fun `an archived solved day is listed but leaves the streak unchanged`() = runTest {
        val today = HistoryTestSupport.TODAY
        val archivedDay = today.minusDays(2)
        val daily = FakeDailyRepository().apply {
            setHistory(
                ZIP,
                listOf(
                    HistoryTestSupport.entry(today),
                    HistoryTestSupport.entry(today.minusDays(1)),
                    HistoryTestSupport.entry(archivedDay).copy(archived = true),
                ),
            )
        }
        // All three solved; the archived day would extend the run to 3 if it counted.
        val completions = FakeCompletionRepository(
            listOf(today, today.minusDays(1), archivedDay).map(HistoryTestSupport::record),
        )

        val state = HistoryTestSupport.viewModel(daily, completions).uiState
            .first { it.rows.isNotEmpty() && it.stats != null }

        assertEquals(3, state.rows.size)
        assertEquals(HistoryTestSupport.record(archivedDay), state.rows.single { it.day == archivedDay }.record)
        assertEquals(2, state.stats?.currentStreak)
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
}
