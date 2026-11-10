package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.DailyDay
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
 * flows (newest first, live via [addEntry]), cached days ([setCached], read uncounted by
 * [cachedPuzzle]), per-game
 * [archiveRange]s, per-game [puzzleNumber]s ([setPuzzleNumbers]; none by default),
 * per-game [linkedInGameTypeId]s ([setLinkedInGameType]; none by default) and
 * per-day scripted [day] outcomes (counted). An unscripted [today]
 * fails loudly — history surfaces are cache-only (A8) and must never fetch. An unscripted
 * [day] mirrors the real read: the [setCached] puzzle (numbered from history) is Ready,
 * no archive is NotCaptured, outside the range is OutOfRange, and an in-range miss —
 * which would fetch — fails loudly.
 */
class FakeDailyRepository : DailyRepository {

    private val todayResults = mutableMapOf<GameId, DailyToday>()
    private val calls = mutableMapOf<GameId, Int>()
    private val histories = mutableMapOf<GameId, MutableStateFlow<List<DailyEntry>>>()
    private val cachedByDay = mutableMapOf<Pair<GameId, LocalDate>, Puzzle>()
    private val archiveRanges = mutableMapOf<GameId, ClosedRange<LocalDate>>()
    private val dayResults = mutableMapOf<Pair<GameId, LocalDate>, DailyDay>()
    private val dayCallCounts = mutableMapOf<Pair<GameId, LocalDate>, Int>()
    private val puzzleNumbers = mutableMapOf<GameId, (LocalDate) -> Int?>()
    private val linkedInGameTypeIds = mutableMapOf<GameId, Int>()

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

    /** Captures [game]'s [day] as [puzzle]; an unscripted [day] serves it as Ready. */
    fun setCached(game: GameId, day: LocalDate, puzzle: Puzzle) {
        cachedByDay[game to day] = puzzle
    }

    /** Declares [game]'s openable days ([range] null = no archive, the default). */
    fun setArchiveRange(game: GameId, range: ClosedRange<LocalDate>?) {
        if (range == null) archiveRanges -= game else archiveRanges[game] = range
    }

    /** Scripts every following [day] outcome for [game]'s [day]. */
    fun setDay(game: GameId, day: LocalDate, result: DailyDay) {
        dayResults[game to day] = result
    }

    /** How many times [day] ran for [game]'s [day]. */
    fun dayCalls(game: GameId, day: LocalDate): Int = dayCallCounts[game to day] ?: 0

    /** Declares [game]'s source numbering ([numbering] null = none, the default). */
    fun setPuzzleNumbers(game: GameId, numbering: ((LocalDate) -> Int?)?) {
        if (numbering == null) puzzleNumbers -= game else puzzleNumbers[game] = numbering
    }

    /** Declares [game]'s LinkedIn type id ([typeId] null = none, the default). */
    fun setLinkedInGameType(game: GameId, typeId: Int?) {
        if (typeId == null) linkedInGameTypeIds -= game else linkedInGameTypeIds[game] = typeId
    }

    override suspend fun today(game: GameId): DailyToday {
        calls[game] = todayCalls(game) + 1
        return todayResults[game] ?: error("no today() scripted for ${game.value} (history is cache-only, A8)")
    }

    override fun observeHistory(game: GameId): Flow<List<DailyEntry>> = history(game)

    override suspend fun day(game: GameId, day: LocalDate): DailyDay {
        val key = game to day
        dayCallCounts[key] = dayCalls(game, day) + 1
        dayResults[key]?.let { return it }
        cachedByDay[key]?.let { puzzle ->
            return DailyDay.Ready(puzzle, history(game).value.firstOrNull { it.day == day }?.puzzleNumber)
        }
        val range = archiveRanges[game] ?: return DailyDay.NotCaptured
        if (day !in range) return DailyDay.OutOfRange
        error("no day() scripted for ${game.value} $day (an in-range miss would fetch)")
    }

    override fun archiveRange(game: GameId): ClosedRange<LocalDate>? = archiveRanges[game]

    override fun puzzleNumber(game: GameId, day: LocalDate): Int? = puzzleNumbers[game]?.invoke(day)

    override fun linkedInGameTypeId(game: GameId): Int? = linkedInGameTypeIds[game]

    override suspend fun cachedPuzzle(game: GameId, day: LocalDate): Puzzle? = cachedByDay[game to day]

    private fun history(game: GameId): MutableStateFlow<List<DailyEntry>> =
        histories.getOrPut(game) { MutableStateFlow(emptyList()) }
}
