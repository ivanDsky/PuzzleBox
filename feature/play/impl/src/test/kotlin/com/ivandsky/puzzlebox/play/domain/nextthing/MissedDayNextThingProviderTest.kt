package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.nextthing.NextThing
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L3: a missed day is a captured day of the last six with no record, for the game just solved (alpha here). */
class MissedDayNextThingProviderTest {

    private fun unsolved(game: GameId, daysAgo: Long) = GameHistoryDay(game, TODAY.minusDays(daysAgo), puzzleNumber = 300, record = null)

    private fun solved(game: GameId, daysAgo: Long): GameHistoryDay {
        val day: LocalDate = TODAY.minusDays(daysAgo)
        val record = CompletionRecord(Ref(game, PuzzleKey.Daily(day, 300)), time = 41_000L, hints = 0, stats = RecordStats(schemaVersion = 1, blob = "{}"))
        return GameHistoryDay(game, day, puzzleNumber = 300, record = record)
    }

    private suspend fun offer(vararg history: GameHistoryDay) =
        MissedDayNextThingProvider.offer(nextThingFacts(history = history.toList()))

    @Test
    fun `the most recent unsolved day of the game just solved is offered`() = runTest {
        val offer = offer(unsolved(alpha, daysAgo = 5), unsolved(beta, daysAgo = 2), unsolved(alpha, daysAgo = 4))

        assertEquals(NextThing.MissedDay(alpha, TODAY.minusDays(4)), offer)
    }

    @Test
    fun `a day missed only for another game is not offered`() = runTest {
        assertNull(offer(unsolved(beta, daysAgo = 2)))
    }

    @Test
    fun `a solved day is not missed`() = runTest {
        val offer = offer(solved(alpha, daysAgo = 1), unsolved(alpha, daysAgo = 3))

        assertEquals(NextThing.MissedDay(alpha, TODAY.minusDays(3)), offer)
    }

    @Test
    fun `the sixth day back is still offered`() = runTest {
        assertEquals(NextThing.MissedDay(alpha, TODAY.minusDays(6)), offer(unsolved(alpha, daysAgo = 6)))
    }

    @Test
    fun `the seventh day back is too old`() = runTest {
        assertNull(offer(unsolved(alpha, daysAgo = 7)))
    }

    @Test
    fun `today's unsolved puzzle is not a missed day`() = runTest {
        assertNull(offer(unsolved(alpha, daysAgo = 0)))
    }
}
