package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
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
 * surface in one projection next to the Zip captured days — no `as? ZipPuzzleRef`
 * casting anywhere on the path.
 */
class GameHistoryDaysTest {

    private class FakeZipDailyRepository(history: List<ZipDailyEntry> = emptyList()) :
        ZipDailyRepository {
        val historyFlow = MutableStateFlow(history)
        override suspend fun today() = error("cache-only (A8)")
        override suspend fun cached(day: LocalDate): Puzzle? = null
        override fun observeHistory(): Flow<List<ZipDailyEntry>> = historyFlow
    }

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

    private fun queensRecord(day: LocalDate, puzzleNumber: Int? = 916) = CompletionRecord(
        puzzleRef = QueensPuzzleRef(day, puzzleNumber),
        time = 41_000L,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    private fun zipRecord(day: LocalDate) = CompletionRecord(
        puzzleRef = ZipPuzzleRef(day, 592),
        time = 95_000L,
        hints = 2,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    @Test
    fun `a queens completion surfaces like a zip completion`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            zip = FakeZipDailyRepository(),
            completions = FakeCompletionRepository(listOf(queensRecord(day))),
        ).first()

        assertEquals(listOf(GameHistoryDay(GameId("queens"), day, 916, queensRecord(day))), rows)
    }

    @Test
    fun `a zip completion still surfaces - regression guard`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            zip = FakeZipDailyRepository(),
            completions = FakeCompletionRepository(listOf(zipRecord(day))),
        ).first()

        assertEquals(1, rows.size)
        assertEquals(ZipDefinition.id, rows.single().gameId)
        assertEquals(zipRecord(day), rows.single().record)
    }

    @Test
    fun `a captured-but-unfinished zip day keeps its row`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            zip = FakeZipDailyRepository(listOf(ZipDailyEntry(day, 592, "zip/n5", 0L))),
            completions = FakeCompletionRepository(),
        ).first()

        assertEquals(
            listOf(GameHistoryDay(ZipDefinition.id, day, 592, record = null)),
            rows,
        )
    }

    @Test
    fun `a solved zip day merges into its captured row - no duplicate`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            zip = FakeZipDailyRepository(listOf(ZipDailyEntry(day, 592, "zip/n5", 0L))),
            completions = FakeCompletionRepository(listOf(zipRecord(day))),
        ).first()

        assertEquals(1, rows.size)
        assertEquals(ZipDefinition.id, rows.single().gameId)
        assertEquals(592, rows.single().puzzleNumber)
        assertEquals(zipRecord(day), rows.single().record)
    }

    @Test
    fun `rows sort newest-first across games`() = runTest {
        val newest = LocalDate.of(2026, 11, 1)
        val mid = LocalDate.of(2026, 10, 30)
        val oldest = LocalDate.of(2026, 10, 29)
        val rows = gameHistoryDays(
            zip = FakeZipDailyRepository(listOf(ZipDailyEntry(mid, 590, "zip/n5", 0L))),
            completions = FakeCompletionRepository(
                listOf(queensRecord(newest), zipRecord(oldest)),
            ),
        ).first()

        assertEquals(
            listOf(newest, mid, oldest),
            rows.map { it.day },
        )
        assertEquals(
            listOf(GameId("queens"), ZipDefinition.id, ZipDefinition.id),
            rows.map { it.gameId },
        )
    }
}
