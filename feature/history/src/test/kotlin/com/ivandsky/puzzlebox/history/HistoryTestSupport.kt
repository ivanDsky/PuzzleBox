package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Game-free history fixtures (ADR-0001: :feature:history never sees a games:* module):
 * stand-in [TestGames] plugins registered under the real ids, so rows read
 * "Daily Zip/Tango/Queens" exactly as production names them through the registry.
 */
object HistoryTestSupport {

    val ZIP = GameId("zip")
    val TANGO = GameId("tango")
    val QUEENS = GameId("queens")

    const val PUZZLE_NUMBER = 592
    const val TANGO_PUZZLE_NUMBER = 301
    const val QUEENS_PUZZLE_NUMBER = 916

    val registry: GameRegistry = TestGames.registry(listOf(ZIP, TANGO, QUEENS))

    /** History's ViewModel over [daily]/[completions] and the production use cases. */
    fun viewModel(daily: DailyRepository, completions: CompletionRepository): HistoryViewModel =
        HistoryViewModel(
            observeGameHistory = ObserveGameHistory(registry, daily, completions),
            computeStreak = ComputeStreak(),
            getReplaySession = GetReplaySession(daily, registry),
            registry = registry,
        )

    fun entry(day: LocalDate, puzzleNumber: Int? = PUZZLE_NUMBER, fetchedAtMs: Long = 0L) = DailyEntry(
        gameId = ZIP,
        day = day,
        puzzleNumber = puzzleNumber,
        variant = "zip/n5",
        fetchedAtMs = fetchedAtMs,
    )

    fun record(day: LocalDate, timeMs: Long = 95_000, hints: Int = 2) = CompletionRecord(
        puzzleRef = TestGames.Ref(ZIP, day, PUZZLE_NUMBER),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":0}"""),
    )

    fun tangoEntry(day: LocalDate, puzzleNumber: Int? = TANGO_PUZZLE_NUMBER, fetchedAtMs: Long = 0L) = DailyEntry(
        gameId = TANGO,
        day = day,
        puzzleNumber = puzzleNumber,
        variant = "tango/n6",
        fetchedAtMs = fetchedAtMs,
    )

    fun tangoRecord(day: LocalDate, timeMs: Long = 61_000, hints: Int = 0) = CompletionRecord(
        puzzleRef = TestGames.Ref(TANGO, day, TANGO_PUZZLE_NUMBER),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":0}"""),
    )

    fun queensRecord(day: LocalDate) = CompletionRecord(
        puzzleRef = TestGames.Ref(QUEENS, day, QUEENS_PUZZLE_NUMBER),
        time = 41_000L,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )
}

/** Epoch-ms for a day's start (UTC) — stable "captured" timestamps in tests. */
fun fetchedAtMs(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
