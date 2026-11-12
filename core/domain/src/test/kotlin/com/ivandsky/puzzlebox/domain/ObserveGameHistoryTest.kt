package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.game.PuzzleRef
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
import kotlinx.coroutines.flow.flowOf
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

    /** A friend-puzzle ref (ADR-0018): its key is custom, so it has no date. */
    private data class CustomRef(override val gameId: GameId, override val key: PuzzleKey) : PuzzleRef

    private val games = listOf(alpha, beta)

    private fun record(game: GameId, day: LocalDate, puzzleNumber: Int? = 916, matchCode: String? = null) =
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(day, puzzleNumber)),
            time = 41_000L,
            hints = 1,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            matchCode = matchCode,
        )

    private fun entry(game: GameId, day: LocalDate, puzzleNumber: Int? = 592, fetchedAtMs: Long = 0L) =
        DailyEntry(game, day, puzzleNumber, "${game.value}/v1", fetchedAtMs)

    private fun daily(vararg entries: DailyEntry) = FakeDailyRepository().apply { entries.forEach(::addEntry) }

    /** The use case over a registry of exactly [games] (registration order = list order). */
    private fun gameHistoryDays(
        daily: DailyRepository,
        games: List<GameId>,
        completions: FakeCompletionRepository,
        duelOpponents: Map<String, String> = emptyMap(),
    ) = ObserveGameHistory(TestGames.registry(games), daily, completions, flowOf(duelOpponents))()

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

    @Test
    fun `a custom record beside the dailies adds no history row`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val custom = record(alpha, day).copy(puzzleRef = CustomRef(alpha, PuzzleKey.Custom("ABC123")))
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(custom, record(beta, day))),
        ).first()

        assertEquals(listOf(GameHistoryDay(beta, day, 916, record(beta, day))), rows)
    }

    @Test
    fun `a duel-solved day carries the match code and the opponent's name`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day, matchCode = "K7Q2MX"))),
            duelOpponents = mapOf("K7Q2MX" to "Sam"),
        ).first()

        assertEquals(DuelOrigin("K7Q2MX", "Sam"), rows.single().duel)
    }

    @Test
    fun `a duel-solved day with no known name still carries the origin`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day, matchCode = "K7Q2MX"))),
        ).first()

        assertEquals(DuelOrigin("K7Q2MX", null), rows.single().duel)
    }

    @Test
    fun `a solo solve carries no duel origin`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day))),
            duelOpponents = mapOf("K7Q2MX" to "Sam"),
        ).first()

        assertEquals(null, rows.single().duel)
    }

    @Test
    fun `a duel solve merges into its captured day and keeps the origin`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, day, 592)),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day, matchCode = "K7Q2MX"))),
            duelOpponents = mapOf("K7Q2MX" to "Sam"),
        ).first()

        assertEquals(1, rows.size)
        assertEquals(DuelOrigin("K7Q2MX", "Sam"), rows.single().duel)
    }

    @Test
    fun `a duel record of a custom puzzle adds no row`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val custom = record(alpha, day, matchCode = "K7Q2MX")
            .copy(puzzleRef = CustomRef(alpha, PuzzleKey.Custom("ABC123")))
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(custom)),
            duelOpponents = mapOf("K7Q2MX" to "Sam"),
        ).first()

        assertEquals(emptyList<GameHistoryDay>(), rows)
    }

    @Test
    fun `an import that is the record flags its captured day`() = runTest {
        val day = LocalDate.of(2024, 3, 4)
        val import = record(alpha, day).copy(isRecord = true, source = CompletionSource.LinkedIn)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, day, fetchedAtMs = 5L).copy(archived = true)),
            games = games,
            completions = FakeCompletionRepository(listOf(import)),
        ).first()

        assertEquals(
            listOf(
                GameHistoryDay(alpha, day, 592, import, fetchedAtMs = 5L, archived = true, fromLinkedIn = true),
            ),
            rows,
        )
    }

    @Test
    fun `an import that is the record flags its record-only day`() = runTest {
        val day = LocalDate.of(2024, 3, 4)
        val import = record(alpha, day).copy(isRecord = true, source = CompletionSource.LinkedIn)
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(import)),
        ).first()

        assertEquals(listOf(GameHistoryDay(alpha, day, 916, import, fromLinkedIn = true)), rows)
    }

    @Test
    fun `an import play-again beside an app record flags the day and keeps the app record`() = runTest {
        val day = LocalDate.of(2024, 3, 4)
        val playAgain = record(alpha, day).copy(time = 9_000L, hints = 0, source = CompletionSource.LinkedIn)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, day, fetchedAtMs = 5L).copy(archived = true)),
            games = games,
            completions = FakeCompletionRepository(listOf(record(alpha, day), playAgain)),
        ).first()

        assertEquals(
            listOf(
                GameHistoryDay(
                    alpha, day, 592, record(alpha, day), fetchedAtMs = 5L, archived = true, fromLinkedIn = true,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a day with no import is not flagged, beside one that is`() = runTest {
        val importedDay = LocalDate.of(2024, 3, 4)
        val appDay = LocalDate.of(2024, 3, 5)
        val import = record(alpha, importedDay).copy(isRecord = true, source = CompletionSource.LinkedIn)
        val rows = gameHistoryDays(
            daily = daily(entry(alpha, appDay), entry(beta, importedDay)),
            games = games,
            completions = FakeCompletionRepository(listOf(import, record(alpha, appDay))),
        ).first()

        assertEquals(
            listOf(
                GameHistoryDay(alpha, appDay, 592, record(alpha, appDay)),
                GameHistoryDay(alpha, importedDay, 916, import, fromLinkedIn = true),
                // The same day in another game has no import of its own.
                GameHistoryDay(beta, importedDay, 592, record = null),
            ),
            rows,
        )
    }

    @Test
    fun `an unlisted game's import adds no row and flags nothing`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)), unlisted = setOf(beta))
        val import = record(beta, day).copy(isRecord = true, source = CompletionSource.LinkedIn)
        val rows = ObserveGameHistory(
            registry,
            daily(entry(beta, day)),
            FakeCompletionRepository(listOf(import, record(alpha, day))),
        )().first()

        assertEquals(listOf(GameHistoryDay(alpha, day, 916, record(alpha, day))), rows)
    }

    @Test
    fun `a custom puzzle's import adds no row and flags nothing`() = runTest {
        val day = LocalDate.of(2026, 11, 1)
        val custom = record(alpha, day).copy(
            puzzleRef = CustomRef(alpha, PuzzleKey.Custom("ABC123")),
            isRecord = true,
            source = CompletionSource.LinkedIn,
        )
        val rows = gameHistoryDays(
            daily = daily(),
            games = games,
            completions = FakeCompletionRepository(listOf(custom, record(alpha, day))),
        ).first()

        assertEquals(listOf(GameHistoryDay(alpha, day, 916, record(alpha, day))), rows)
    }
}
