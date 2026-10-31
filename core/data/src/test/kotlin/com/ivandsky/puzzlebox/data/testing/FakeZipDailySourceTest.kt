package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.data.repository.ZipDailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeZipDailySourceTest {

    private val today = LocalDate.of(2026, 10, 31)

    private fun envelope(): Puzzle {
        val daily = ZipWireParser.parse(
            """
            {"gridSize":5,"orderedSequence":[0,12,24],
             "solution":[0,1,2,3,4,9,8,7,6,5,10,11,12,13,14,19,18,17,16,15,20,21,22,23,24]}
            """.trimIndent(),
            today,
            "600",
        )
        daily as DecodeResult.Ok
        return Puzzle(ZipDefinition.id, daily.value.puzzle.variant, ZipCodec.VERSION, ZipCodec.encode(daily.value.puzzle))
    }

    @Test
    fun `default fails Unavailable and records the requested dates`() = runTest {
        val fake = FakeZipDailySource()

        val result = fake.fetchDaily(today)

        assertTrue(result is FetchResult.Err && result.error is FetchError.Unavailable)
        assertEquals(listOf(today), fake.requestedDates)
    }

    @Test
    fun `queued puzzle becomes a Daily with the requested day as ref date`() = runTest {
        val fake = FakeZipDailySource()
        fake.nextPuzzle = envelope()
        fake.nextPuzzleNumber = 600

        val result = fake.fetchDaily(LocalDate.of(2026, 11, 1))

        assertTrue(result is FetchResult.Ok)
        val daily = (result as FetchResult.Ok).value
        assertEquals("zip", daily.puzzle.gameId.value)
        assertEquals(LocalDate.of(2026, 11, 1), daily.ref.date)
        assertEquals(600, daily.ref.puzzleNumber)
    }
}
