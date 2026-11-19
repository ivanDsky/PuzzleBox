package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first

/**
 * A fake history for the recap tests: two games, solves of a chosen puzzle day, and the real merged projection
 * ([ObserveGameHistory]) over them, so the tests count what the game page counts. [now] is the clock the streak walk reads.
 */
internal class RecapHistoryFixture(now: Instant = Instant.parse("2026-12-15T12:00:00Z")) {
    val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)))
    val completions = FakeCompletionRepository()
    val daily = FakeDailyRepository()
    val observeHistory = ObserveGameHistory(registry, daily, completions)

    /** The streak walk the game page uses, "today" being [now]'s puzzle day. */
    val computeStreak = ComputeStreak(GetPuzzleDay(registry, Clock.fixed(now, ZoneOffset.UTC)))
    val computePeriodFacts = ComputePeriodFacts(computeStreak, ReplaySolveFacts(computeStreak, registry))

    /** The puzzle day every game reads; move it with `setAll`. */
    val calendar = FakeGameCalendar(LocalDate.of(2026, 11, 20))

    fun availability() = ObserveRecapAvailability(observeHistory, calendar, registry)

    /** Replaces every stored row. */
    fun store(vararg records: CompletionRecord) = completions.setRecords(records.toList())

    suspend fun history(): List<GameHistoryDay> = observeHistory().first()

    /** [game]'s record solve of the puzzle [day]; a null [puzzleNumber] is a practice play. */
    fun solve(game: GameId, day: LocalDate, time: Long = 30_000L, hints: Int = 0, puzzleNumber: Int? = 100) =
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(day, puzzleNumber)),
            time = time,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    /** [game]'s [day] captured as an archive day (a past puzzle opened from the archive), not its own day's puzzle. */
    fun captureArchived(game: GameId, day: LocalDate, puzzleNumber: Int? = 100) =
        daily.addEntry(DailyEntry(game, day, puzzleNumber, variant = "", fetchedAtMs = 0L, archived = true))

    /** The same solve as a LinkedIn import stamped at [importedAt], long after its puzzle day. */
    fun imported(game: GameId, day: LocalDate, importedAt: Instant = Instant.parse("2026-11-05T09:00:00Z")) =
        solve(game, day).copy(source = CompletionSource.LinkedIn, firstCompletedAt = importedAt)

    /** An imported play of a day already solved in the app: stored beside the app record, never the day's record row. */
    fun playAgain(game: GameId, day: LocalDate) = imported(game, day).copy(isRecord = false)
}
