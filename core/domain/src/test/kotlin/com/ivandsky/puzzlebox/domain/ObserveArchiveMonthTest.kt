package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An archive month's days (spec A26/A27) over the core:testing fakes; local data only, never a fetch. */
class ObserveArchiveMonthTest {

    /** A friend-puzzle ref (ADR-0018): its key is custom, so it has no date. */
    private data class CustomRef(override val gameId: GameId, override val key: PuzzleKey) : PuzzleRef

    private val day1 = LocalDate.of(2025, 3, 18)
    private val today = LocalDate.of(2026, 11, 5)
    private val day = LocalDate.of(2026, 10, 20)

    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository().apply { setArchiveRange(alpha, day1..today) }
    private val calendar = FakeGameCalendar(today)

    private fun month(month: YearMonth, game: GameId = alpha, calendar: FakeGameCalendar = this.calendar) =
        ObserveArchiveMonth(daily, saved, completions, calendar)(game, month)

    private fun record(time: Long, game: GameId = alpha, on: LocalDate = day) =
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(on, null)),
            time = time,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    /** Captures a real puzzle for [on]: a history row whose cached puzzle reads back. */
    private fun store(on: LocalDate = day, game: GameId = alpha, fetchedAtMs: Long = 0L, archived: Boolean = false) {
        daily.setCached(game, on, TestGames.puzzle(game))
        storePlaceholder(on, game, fetchedAtMs, archived)
    }

    /** A history row whose cached puzzle reads null, as a past day's seed placeholder does (A10). */
    private fun storePlaceholder(on: LocalDate = day, game: GameId = alpha, fetchedAtMs: Long = 0L, archived: Boolean = false) =
        daily.addEntry(DailyEntry(game, on, null, variant = "v", fetchedAtMs = fetchedAtMs, archived = archived))

    private fun List<ArchiveDay>.on(date: LocalDate): ArchiveDay = single { it.date == date }

    @After
    fun neverFetches() {
        assertEquals(0, daily.dayCalls(alpha, day))
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `#1's month starts on #1 and numbers from it`() = runTest {
        val days = month(YearMonth.from(day1)).first()

        assertEquals((18..31).map { LocalDate.of(2025, 3, it) }, days.map { it.date })
        assertEquals((1..14).toList(), days.map { it.number })
    }

    @Test
    fun `today's month ends on today`() = runTest {
        val days = month(YearMonth.from(today)).first()

        assertEquals((1..5).map { LocalDate.of(2026, 11, it) }, days.map { it.date })
        assertEquals(ChronoUnit.DAYS.between(day1, today).toInt() + 1, days.last().number)
    }

    @Test
    fun `months before #1 or after today have no days`() = runTest {
        assertEquals(emptyList<ArchiveDay>(), month(YearMonth.of(2025, 2)).first())
        assertEquals(emptyList<ArchiveDay>(), month(YearMonth.of(2026, 12)).first())
    }

    @Test
    fun `the source's numbering wins over the day's position`() = runTest {
        daily.setPuzzleNumbers(alpha) { 500 + it.dayOfMonth }

        assertEquals(520, month(YearMonth.from(day)).first().on(day).number)
    }

    @Test
    fun `untouched days need a connection and are never opened`() = runTest {
        val untouched = month(YearMonth.from(day)).first().on(day)

        assertEquals(PastDayStatus.NeedsConnection, untouched.status)
        assertFalse(untouched.archived)
        assertNull(untouched.openedOn)
    }

    @Test
    fun `status precedence is Solved over InProgress over Stored`() = runTest {
        val solvedDay = day
        val playingDay = day.plusDays(1)
        val storedDay = day.plusDays(2)
        val placeholderDay = day.plusDays(3)
        listOf(solvedDay, playingDay, storedDay).forEach { store(on = it) }
        storePlaceholder(on = placeholderDay)
        saved.save(PlaySlot.Daily(alpha, solvedDay), byteArrayOf(1), elapsedMs = 9_000L)
        saved.save(PlaySlot.Daily(alpha, playingDay), byteArrayOf(1), elapsedMs = 7_000L)
        completions.onCompleted(record(time = 42_000L, on = solvedDay))
        completions.onCompleted(record(time = 1L, game = beta, on = storedDay))

        val days = month(YearMonth.from(day)).first()

        assertEquals(PastDayStatus.Solved(42_000L), days.on(solvedDay).status)
        assertEquals(PastDayStatus.InProgress(7_000L), days.on(playingDay).status)
        assertEquals(PastDayStatus.Stored, days.on(storedDay).status)
        assertEquals(PastDayStatus.NeedsConnection, days.on(placeholderDay).status)
    }

    @Test
    fun `a captured day carries its archived flag and first-opened day`() = runTest {
        val openedMs = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()
        store(on = day, fetchedAtMs = openedMs, archived = true)
        store(on = day.plusDays(1))

        val days = month(YearMonth.from(day)).first()

        assertTrue(days.on(day).archived)
        assertEquals(LocalDate.of(2026, 11, 1), days.on(day).openedOn)
        assertFalse(days.on(day.plusDays(1)).archived)
    }

    @Test
    fun `the month updates when a day is solved`() = runTest {
        store()

        month(YearMonth.from(day)).test {
            assertEquals(PastDayStatus.Stored, awaitItem().on(day).status)
            completions.onCompleted(record(time = 30_000L))
            assertEquals(PastDayStatus.Solved(30_000L), awaitItem().on(day).status)
        }
    }

    @Test
    fun `a game without an archive has no days`() = runTest {
        store(game = beta)

        assertEquals(emptyList<ArchiveDay>(), month(YearMonth.from(day), game = beta).first())
    }

    @Test
    fun `a game in a zone ahead reads its own today and opened day`() = runTest {
        // Strands rolls over at New York midnight, three hours before the LA-day games.
        val strandsToday = today.plusDays(1)
        val newYork = FakeGameCalendar(today, ZoneId.of("America/New_York")).apply { set(beta, strandsToday) }
        daily.setArchiveRange(beta, day1..strandsToday)
        // 03:30 UTC on Nov 6 is still Nov 5 in New York.
        val openedMs = Instant.parse("2026-11-06T03:30:00Z").toEpochMilli()
        store(on = day, game = beta, fetchedAtMs = openedMs, archived = true)

        val days = month(YearMonth.from(today), game = beta, calendar = newYork).first()

        assertEquals(strandsToday, days.last().date)
        assertEquals(LocalDate.of(2026, 11, 5), month(YearMonth.from(day), beta, newYork).first().on(day).openedOn)
    }

    @Test
    fun `a custom record beside the dailies fills no archive cell`() = runTest {
        val custom = record(time = 9_000L).copy(puzzleRef = CustomRef(alpha, PuzzleKey.Custom("ABC123")))
        completions.setRecords(listOf(custom, record(time = 12_000L, on = day.minusDays(1))))

        val days = month(YearMonth.from(day)).first()

        assertEquals(PastDayStatus.Solved(12_000L), days.on(day.minusDays(1)).status)
        assertEquals(listOf(day.minusDays(1)), days.filter { it.status is PastDayStatus.Solved }.map { it.date })
    }
}
