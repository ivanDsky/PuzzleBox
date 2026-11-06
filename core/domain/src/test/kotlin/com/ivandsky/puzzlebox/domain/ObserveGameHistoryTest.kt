package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The game-agnostic merged history (fix round 5, defect C): records from EVERY game
 * surface in one projection next to every listed game's captured days — no casting to a
 * concrete ref anywhere on the path.
 */
class ObserveGameHistoryTest {

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

    /** The use case over a registry of exactly [games] (registration order = list order). */
    private fun gameHistoryDays(daily: DailyRepository, games: List<GameId>, completions: FakeCompletionRepository) =
        ObserveGameHistory(TestGames.registry(games), daily, completions)()

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
    fun `games outside the list contribute no captured days`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(entry(beta, day)),
            games = listOf(alpha),
            completions = FakeCompletionRepository(),
        ).first()

        assertEquals(emptyList<GameHistoryDay>(), rows)
    }

    @Test
    fun `an unlisted game's completion surfaces nowhere`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)), unlisted = setOf(beta))
        val rows = ObserveGameHistory(
            registry,
            daily(entry(beta, day)),
            FakeCompletionRepository(listOf(record(beta, day), record(alpha, day))),
        )().first()

        assertEquals(listOf(alpha), rows.map { it.gameId })
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

    @Test
    fun `an archived solved day keeps its row and carries the archived flag`() = runTest {
        val day = LocalDate.of(2024, 3, 4)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, day).copy(archived = true)),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day))),
        ).first()

        assertEquals(true, rows.single().archived)
        assertEquals(record(alpha, day), rows.single().record)
    }

    @Test
    fun `a record-only row is not archived`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(record(beta, day))),
        ).first()

        assertEquals(false, rows.single().archived)
    }
}
