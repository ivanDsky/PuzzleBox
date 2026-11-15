package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRefresh
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scriptable in-memory [DailyRepository] for feature and app tests, keyed by [GameId]
 * like the real one: per-game today's served puzzle ([setToday]) or failure ([setTodayUnavailable]) answering
 * [refreshToday], or a scripted [setRefresh] outcome (counted by [todayCalls]),
 * per-game history flows (newest first, live via [addEntry]), cached days ([setCached], read
 * uncounted by [cachedPuzzle]; [replaceToday] compares and replaces the game's stored today),
 * per-game
 * [archiveRange]s, per-game [puzzleNumber]s ([setPuzzleNumbers]; none by default),
 * per-game [linkedInGameTypeId]s ([setLinkedInGameType]; none by default), per-game
 * [linkedInGamePath]s ([setLinkedInGamePath]; none by default) and
 * per-day scripted [day] outcomes (counted). An unscripted [refreshToday]
 * fails loudly — history surfaces are cache-only (A8) and must never fetch. An unscripted
 * [day] mirrors the real read: the [setCached] puzzle (numbered from history) is Ready,
 * no archive is NotCaptured, outside the range is OutOfRange, and an in-range miss —
 * which would fetch — fails loudly.
 */
class FakeDailyRepository : DailyRepository {

    private val served = mutableMapOf<GameId, Puzzle>()
    private val failures = mutableMapOf<GameId, FetchError>()
    /** What a refresh stored as today, with the day the play asked for when it did (null: nobody asked, any day). */
    private class StoredToday(val day: LocalDate?, val puzzle: Puzzle)

    private val storedToday = mutableMapOf<GameId, StoredToday>()
    private val lastAsked = mutableMapOf<GameId, LocalDate>()
    private val refreshGates = mutableMapOf<GameId, CompletableDeferred<Unit>>()
    private val refreshResults = mutableMapOf<GameId, DailyRefresh>()
    private val calls = mutableMapOf<GameId, Int>()
    private val answers = mutableMapOf<GameId, Int>()
    private val histories = mutableMapOf<GameId, MutableStateFlow<List<DailyEntry>>>()
    private val cachedByDay = mutableMapOf<Pair<GameId, LocalDate>, Puzzle>()
    private val archiveRanges = mutableMapOf<GameId, ClosedRange<LocalDate>>()
    private val dayResults = mutableMapOf<Pair<GameId, LocalDate>, DailyDay>()
    private val dayCallCounts = mutableMapOf<Pair<GameId, LocalDate>, Int>()
    private val puzzleNumbers = mutableMapOf<GameId, (LocalDate) -> Int?>()
    private val linkedInGameTypeIds = mutableMapOf<GameId, Int>()
    private val linkedInGamePaths = mutableMapOf<GameId, String>()

    /** The source serves [puzzle] as [game]'s today from now on: the first refresh stores it, later ones compare. */
    fun setToday(game: GameId, puzzle: Puzzle) {
        served[game] = puzzle
        failures -= game
    }

    /** The source can't serve [game]'s today: refreshes fail with [error]. */
    fun setTodayUnavailable(game: GameId, error: FetchError) {
        failures[game] = error
        served -= game
    }

    /** The next refreshes of [game] wait until the returned gate completes, so a test sees the board first. */
    fun holdRefresh(game: GameId): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { refreshGates[game] = it }

    /** Scripts [game]'s next (and every following) [refreshToday] outcome; it overrides [setToday]. */
    fun setRefresh(game: GameId, result: DailyRefresh) {
        refreshResults[game] = result
    }

    /** How many times [refreshToday] ran for [game]. */
    fun todayCalls(game: GameId): Int = calls[game] ?: 0

    /** How many of those refreshes have returned their answer (a held one has not). */
    fun todayAnswers(game: GameId): Int = answers[game] ?: 0

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

    /** Declares [game]'s LinkedIn route ([path] null = none, the default). */
    fun setLinkedInGamePath(game: GameId, path: String?) {
        if (path == null) linkedInGamePaths -= game else linkedInGamePaths[game] = path
    }

    override suspend fun refreshToday(game: GameId): DailyRefresh {
        calls[game] = todayCalls(game) + 1
        refreshGates[game]?.await()
        return answer(game).also { answers[game] = todayAnswers(game) + 1 }
    }

    private fun answer(game: GameId): DailyRefresh {
        refreshResults[game]?.let { return it }
        failures[game]?.let { return DailyRefresh.Failed(it) }
        val puzzle = served[game] ?: error("no today scripted for ${game.value} (history is cache-only, A8)")
        val stored = storedToday[game]?.puzzle ?: newestCachedDay(game)?.let(cachedByDay::get)
        return when {
            stored == null -> DailyRefresh.Stored(puzzle).also { storedToday[game] = StoredToday(lastAsked[game], puzzle) }
            stored == puzzle -> DailyRefresh.Unchanged
            else -> DailyRefresh.Changed(puzzle)
        }
    }

    override suspend fun replaceToday(game: GameId, expected: Puzzle, fresh: Puzzle): Boolean {
        storedToday[game]?.takeIf { it.puzzle == expected }?.let {
            storedToday[game] = StoredToday(it.day, fresh)
            return true
        }
        val today = newestCachedDay(game) ?: return false
        if (cachedByDay[today] != expected) return false
        cachedByDay[today] = fresh
        return true
    }

    private fun newestCachedDay(game: GameId): Pair<GameId, LocalDate>? =
        cachedByDay.keys.filter { it.first == game }.maxByOrNull { it.second }

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

    override fun linkedInGamePath(game: GameId): String? = linkedInGamePaths[game]

    /** Day-keyed: a refresh's stored today answers only the day the play asked for before it stored (A5). */
    override suspend fun cachedPuzzle(game: GameId, day: LocalDate): Puzzle? {
        lastAsked[game] = day
        return cachedByDay[game to day]
            ?: storedToday[game]?.takeIf { it.day == null || it.day == day }?.puzzle
    }

    private fun history(game: GameId): MutableStateFlow<List<DailyEntry>> =
        histories.getOrPut(game) { MutableStateFlow(emptyList()) }
}
