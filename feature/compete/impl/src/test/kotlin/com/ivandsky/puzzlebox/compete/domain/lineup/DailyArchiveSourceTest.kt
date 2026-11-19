package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily archive as the add sheet's Past days rows: paged newest first, played from history, never listed whole. */
class DailyArchiveSourceTest {

    private val today = LocalDate.of(2026, 11, 9)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val fake = FakeDailyRepository().apply {
        setArchiveRange(alpha, today.minusYears(3)..today)
        setPuzzleNumbers(alpha) { day -> (day.toEpochDay() % 10_000).toInt() + 1 }
    }
    private var numberCalls = 0
    private val daily = object : DailyRepository by fake {
        override fun puzzleNumber(game: GameId, day: LocalDate): Int? = fake.puzzleNumber(game, day).also { numberCalls++ }
    }
    private val source = DailyArchiveSource(daily, registry)

    private fun number(day: LocalDate) = (day.toEpochDay() % 10_000).toInt() + 1

    @Test
    fun `a three year archive comes 30 days a page, newest first, and only the page asked for is read`() {
        val first = source.page(source.start(), 30)

        assertEquals(30, first.rows.size)
        assertEquals(LegRef.Daily(alpha, today, number(today)), first.rows.first().ref)
        assertEquals(LegRef.Daily(alpha, today.minusDays(29), number(today.minusDays(29))), first.rows.last().ref)
        assertEquals("Alpha #${number(today)}", first.rows.first().title)
        assertEquals(30, numberCalls)

        val second = source.page(checkNotNull(first.next), 30)

        assertEquals(today.minusDays(30), second.rows.first().ref.day)
        assertEquals(60, numberCalls)
    }

    @Test
    fun `the archive ends with its first day, and a game without an archive has no rows`() {
        fake.setArchiveRange(alpha, today.minusDays(44)..today)
        fake.setArchiveRange(beta, null)

        val first = source.page(source.start(), 30)
        val last = source.page(checkNotNull(first.next), 30)

        assertEquals(15, last.rows.size)
        assertEquals(today.minusDays(44), last.rows.last().ref.day)
        assertTrue(source.page(source.start(), 100).rows.none { it.ref.gameId == beta })
        assertNull(last.next)
        assertNull(source.page(source.start(), 45).next)
    }

    @Test
    fun `two games merge by day, and a day without a number has no row`() {
        fake.setArchiveRange(alpha, today.minusDays(2)..today)
        fake.setArchiveRange(beta, today.minusDays(1)..today)
        fake.setPuzzleNumbers(beta) { day -> if (day == today.minusDays(1)) null else 5 }

        val rows = source.page(source.start(), 30).rows

        assertEquals(
            listOf(alpha to today, beta to today, alpha to today.minusDays(1), alpha to today.minusDays(2)),
            rows.map { it.ref.gameId to it.ref.day },
        )
    }

    @Test
    fun `rows carry the date and Past day source, and played comes from the completions held`() = runTest {
        val day = today.minusDays(1)
        val rows = MutableStateFlow(setOf(LegRef.Daily(alpha, day, number(day)).wire))
        var latest: SourceRows? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe(rows, flowOf(1)).collect { latest = it } }

        val shown = checkNotNull(latest).rows.map { it.puzzle }

        assertEquals(30, shown.size)
        assertEquals(listOf(false, true), shown.take(2).map { it.played })
        assertEquals(PuzzleSource.PastDay, shown[1].entry.source)
        assertEquals("Nov 8, 2026", shown[1].meta)
        assertEquals(listOf(false, false, false), shown.drop(2).take(3).map { it.isNew })
    }

    @Test
    fun `asking for fewer pages than were read starts the read over, so a sheet opened again sees page one then page two`() = runTest {
        val pages = MutableStateFlow(1)
        val sizes = mutableListOf<Int>()
        var latest: SourceRows? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.observe(flowOf(emptySet()), pages).collect { latest = it; sizes += it.rows.size }
        }

        pages.value = 2
        pages.value = 1
        pages.value = 2

        assertEquals(listOf(30, 60, 30, 60), sizes)
        assertEquals(today.minusDays(59), (checkNotNull(latest).rows.last().puzzle.entry.ref as LegRef.Daily).day)
        assertEquals(120, numberCalls)
    }

    @Test
    fun `the source says when no page is left, exactly at the archive's last day`() = runTest {
        fake.setArchiveRange(alpha, today.minusDays(59)..today)
        val pages = MutableStateFlow(1)
        val more = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.observe(flowOf(emptySet()), pages).collect { more += it.moreDays }
        }

        pages.value = 2
        pages.value = 3

        assertEquals(listOf(true, false, false), more)
    }

    @Test
    fun `a page is read only when the sheet asks for it`() = runTest {
        val pages = MutableStateFlow(1)
        var latest: SourceRows? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.observe(flowOf(emptySet()), pages).collect { latest = it }
        }
        assertEquals(30, checkNotNull(latest).rows.size)
        assertEquals(30, numberCalls)

        pages.value = 2

        assertEquals(60, checkNotNull(latest).rows.size)
        assertEquals(60, numberCalls)
        assertEquals(today.minusDays(59), checkNotNull(latest).rows.last().puzzle.entry.ref.let { (it as LegRef.Daily).day })
    }
}
