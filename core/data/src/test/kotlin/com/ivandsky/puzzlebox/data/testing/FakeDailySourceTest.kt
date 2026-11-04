package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.data.testing.TestGames.alpha
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDailySourceTest {

    private val today = LocalDate.of(2026, 10, 31)

    @Test
    fun `default fails Unavailable and records the requested dates`() = runTest {
        val fake = FakeDailySource(alpha)

        val result = fake.fetchDaily(today)

        assertTrue(result is FetchResult.Err && result.error is FetchError.Unavailable)
        assertEquals(listOf(today), fake.requestedDates)
    }

    @Test
    fun `queued puzzle becomes a DailyFetch with the requested day as ref date`() = runTest {
        val fake = FakeDailySource(alpha)
        fake.nextPuzzle = TestGames.puzzle(alpha)
        fake.nextPuzzleNumber = 600

        val result = fake.fetchDaily(LocalDate.of(2026, 11, 1))

        val daily = (result as FetchResult.Ok).value
        assertEquals(alpha, daily.puzzle.gameId)
        assertEquals(alpha, daily.ref.gameId)
        assertEquals(LocalDate.of(2026, 11, 1), daily.ref.date)
        assertEquals(600, daily.ref.puzzleNumber)
    }
}
