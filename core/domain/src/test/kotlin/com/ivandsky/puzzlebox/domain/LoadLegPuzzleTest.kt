package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The puzzle of a duel leg: cached or fetched for a daily leg; stored or opened for a friend's; a failed read told from a settled no. */
class LoadLegPuzzleTest {

    private val game = GameId("Zip")
    private val day = LocalDate.of(2026, 11, 9)
    private val puzzle = Puzzle(game, VariantId("v1"), 1, byteArrayOf(7))
    private val daily = FakeDailyRepository()
    private val load = LoadLegPuzzle(
        daily = daily,
        received = FakeReceivedPuzzleRepository(),
        loadShared = LoadSharedPuzzle(
            registry = GameRegistry(emptyList()),
            shared = FakeSharedPuzzleRepository(),
            received = FakeReceivedPuzzleRepository(),
            budget = { Budget.within(1_000, System::nanoTime) },
            clock = { Instant.EPOCH },
        ),
    )

    private fun found(read: LegPuzzleRead) = (read as LegPuzzleRead.Found).puzzle

    @Test
    fun `a cached daily leg answers from the cache without a fetch`() = runTest {
        daily.setCached(game, day, puzzle)

        assertEquals(puzzle, found(load(LegRef.Daily(game, day, 1))))
        assertEquals(0, daily.dayCalls(game, day))
    }

    @Test
    fun `an uncached daily leg is fetched`() = runTest {
        daily.setDay(game, day, DailyDay.Ready(puzzle, 1))

        assertEquals(puzzle, found(load(LegRef.Daily(game, day, 1))))
        assertEquals(1, daily.dayCalls(game, day))
    }

    @Test
    fun `a daily leg that cannot be fetched is unavailable, one that is not out is absent`() = runTest {
        daily.setDay(game, day, DailyDay.Failed(FetchError.Unavailable("offline")))
        assertEquals(LegPuzzleRead.Unavailable, load(LegRef.Daily(game, day, 1)))

        daily.setDay(game, day, DailyDay.Failed(FetchError.NotFound("404")))
        assertEquals(LegPuzzleRead.Absent, load(LegRef.Daily(game, day, 1)))
    }

    @Test
    fun `a friend's leg that is neither stored nor on the server is absent`() = runTest {
        assertEquals(LegPuzzleRead.Absent, load(LegRef.Custom(game, checkNotNull(ShareCode.parse("ABC234")))))
    }
}
