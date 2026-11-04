package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scriptable in-memory [DailyRepository] for feature and app tests, keyed by [GameId]
 * like the real one: per-game scripted [today] results (counted), per-game history
 * flows (newest first, live via [addEntry]) and a cache-only [cached] map. An
 * unscripted [today] fails loudly — history surfaces are cache-only (A8) and must never
 * fetch.
 */
class FakeDailyRepository : DailyRepository {

    private val todayResults = mutableMapOf<GameId, DailyToday>()
    private val calls = mutableMapOf<GameId, Int>()
    private val histories = mutableMapOf<GameId, MutableStateFlow<List<DailyEntry>>>()
    private val cachedByDay = mutableMapOf<Pair<GameId, LocalDate>, Puzzle>()

    /** Scripts [game]'s next (and every following) [today] outcome. */
    fun setToday(game: GameId, result: DailyToday) {
        todayResults[game] = result
    }

    /** How many times [today] ran for [game]. */
    fun todayCalls(game: GameId): Int = calls[game] ?: 0

    /** Replaces [game]'s captured days (kept newest first, the repository contract). */
    fun setHistory(game: GameId, entries: List<DailyEntry>) {
        history(game).value = entries.sortedByDescending { it.day }
    }

    /** Captures one more day for [entry]'s game; observers see a new emission. */
    fun addEntry(entry: DailyEntry) {
        val flow = history(entry.gameId)
        flow.value = (listOf(entry) + flow.value).sortedByDescending { it.day }
    }

    /** [game]'s current captured days snapshot, for tests that compose screens directly. */
    fun entries(game: GameId): List<DailyEntry> = history(game).value

    fun setCached(game: GameId, day: LocalDate, puzzle: Puzzle) {
        cachedByDay[game to day] = puzzle
    }

    override suspend fun today(game: GameId): DailyToday {
        calls[game] = todayCalls(game) + 1
        return todayResults[game] ?: error("no today() scripted for ${game.value} (history is cache-only, A8)")
    }

    override suspend fun cached(game: GameId, day: LocalDate): Puzzle? = cachedByDay[game to day]

    override fun observeHistory(game: GameId): Flow<List<DailyEntry>> = history(game)

    private fun history(game: GameId): MutableStateFlow<List<DailyEntry>> =
        histories.getOrPut(game) { MutableStateFlow(emptyList()) }
}
