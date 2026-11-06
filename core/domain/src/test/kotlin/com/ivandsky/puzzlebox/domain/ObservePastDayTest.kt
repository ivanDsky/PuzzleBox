package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** One past day's status (spec A20) over the core:testing fakes; local data only, never a fetch. */
class ObservePastDayTest {

    private val day = LocalDate.of(2026, 10, 20)
    private val otherDay = day.minusDays(1)

    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository()

    private fun pastDay() = ObservePastDay(daily, saved, completions)(alpha, day)

    private fun record(time: Long, game: GameId = alpha, on: LocalDate = day) =
        CompletionRecord(
            puzzleRef = Ref(game, on),
            time = time,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    private fun store(on: LocalDate = day, number: Int? = null, game: GameId = alpha) =
        daily.addEntry(DailyEntry(game, on, number, variant = "v", fetchedAtMs = 0L))

    @After
    fun neverFetches() {
        assertEquals(0, daily.dayCalls(alpha, day))
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `nothing on device reads NeedsConnection`() = runTest {
        store(on = otherDay)
        store(game = beta)

        val past = pastDay().first()

        assertEquals(PastDay(alpha, day, null, PastDayStatus.NeedsConnection), past)
    }

    @Test
    fun `a stored never-played day reads Stored`() = runTest {
        store()

        assertEquals(PastDayStatus.Stored, pastDay().first().status)
    }

    @Test
    fun `a saved play beats Stored`() = runTest {
        store()
        saved.save(PlaySlot(alpha, day), byteArrayOf(1), elapsedMs = 42_000L)
        saved.save(PlaySlot(alpha, otherDay), byteArrayOf(1), elapsedMs = 1L)

        assertEquals(PastDayStatus.InProgress(42_000L), pastDay().first().status)
    }

    @Test
    fun `a record beats a saved play`() = runTest {
        store()
        saved.save(PlaySlot(alpha, day), byteArrayOf(1), elapsedMs = 42_000L)
        completions.setRecords(listOf(record(61_000L), record(5L, on = otherDay), record(6L, game = beta)))

        assertEquals(PastDayStatus.Solved(61_000L), pastDay().first().status)
    }

    @Test
    fun `the number comes from the source numbering`() = runTest {
        daily.setPuzzleNumbers(alpha) { if (it == day) 512 else null }

        assertEquals(512, pastDay().first().number)
    }

    @Test
    fun `no source numbering falls back to the stored entry's number`() = runTest {
        store(number = 77)
        assertEquals(77, pastDay().first().number)

        daily.setPuzzleNumbers(alpha) { null }
        store(on = otherDay, number = 3)
        assertEquals(77, pastDay().first().number)
    }

    @Test
    fun `status follows local changes live`() = runTest {
        pastDay().test {
            assertEquals(PastDayStatus.NeedsConnection, awaitItem().status)
            store()
            assertEquals(PastDayStatus.Stored, awaitItem().status)
            saved.save(PlaySlot(alpha, day), byteArrayOf(1), elapsedMs = 9L)
            assertEquals(PastDayStatus.InProgress(9L), awaitItem().status)
            completions.setRecords(listOf(record(10L)))
            assertEquals(PastDayStatus.Solved(10L), awaitItem().status)
            saved.delete(PlaySlot(alpha, day))
            expectNoEvents()
        }
    }
}
