package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.testing.FakeDailyRepository
import com.ivandsky.puzzlebox.data.testing.TestGames.Ref
import com.ivandsky.puzzlebox.data.testing.TestGames.alpha
import com.ivandsky.puzzlebox.data.testing.TestGames.beta
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The game-agnostic merged history (fix round 5, defect C): records from EVERY game
 * surface in one projection next to every listed game's captured days — no casting to a
 * concrete ref anywhere on the path.
 */
class GameHistoryDaysTest {

    private class FakeCompletionRepository(records: List<CompletionRecord> = emptyList()) :
        CompletionRepository {
        private val recordsFlow = MutableStateFlow(records)
        override suspend fun onCompleted(record: CompletionRecord) {
            recordsFlow.value = recordsFlow.value + record
        }
        override fun observeRecords(): Flow<List<CompletionRecord>> = recordsFlow
        override fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
            recordsFlow.map { rows -> rows.filter { it.puzzleRef == puzzleRef } }
    }

    private val games = listOf(alpha, beta)

    private fun record(game: GameId, day: LocalDate, puzzleNumber: Int? = 916) = CompletionRecord(
        puzzleRef = Ref(game, day, puzzleNumber),
        time = 41_000L,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    private fun entry(game: GameId, day: LocalDate, puzzleNumber: Int? = 592, fetchedAtMs: Long = 0L) =
        DailyEntry(game, day, puzzleNumber, "${game.value}/v1", fetchedAtMs)

    private fun daily(vararg entries: DailyEntry) = FakeDailyRepository().apply { entries.forEach(::addEntry) }

    @Test
    fun `a completion surfaces without a captured day`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(record(beta, day))),
        ).first()

        assertEquals(listOf(GameHistoryDay(beta, day, 916, record(beta, day))), rows)
    }

    @Test
    fun `a captured-but-unfinished day keeps its row for every game`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, day, fetchedAtMs = 5L), entry(beta, day, 301, fetchedAtMs = 7L)),
            games = games,
            completions = FakeCompletionRepository(),
        ).first()

        assertEquals(
            listOf(
                GameHistoryDay(alpha, day, 592, record = null, fetchedAtMs = 5L),
                GameHistoryDay(beta, day, 301, record = null, fetchedAtMs = 7L),
            ),
            rows,
        )
    }

    @Test
    fun `a solved day merges into its captured row - no duplicate`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, day, 592)),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day, puzzleNumber = null))),
        ).first()

        assertEquals(1, rows.size)
        assertEquals(alpha, rows.single().gameId)
        assertEquals(592, rows.single().puzzleNumber)
        assertEquals(record(alpha, day, puzzleNumber = null), rows.single().record)
    }

    @Test
    fun `games outside the list contribute records but no captured days`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(entry(beta, day)),
            games = listOf(alpha),
            completions = FakeCompletionRepository(),
        ).first()

        assertEquals(emptyList<GameHistoryDay>(), rows)
    }

    @Test
    fun `rows sort newest-first across games`() = runTest {
        val newest = LocalDate.of(2026, 11, 1)
        val mid = LocalDate.of(2026, 10, 30)
        val oldest = LocalDate.of(2026, 10, 29)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, mid, 590)),
            games = games,
            completions = FakeCompletionRepository(
                listOf(record(beta, newest), record(alpha, oldest)),
            ),
        ).first()

        assertEquals(listOf(newest, mid, oldest), rows.map { it.day })
        assertEquals(listOf(beta, alpha, alpha), rows.map { it.gameId })
    }
}
