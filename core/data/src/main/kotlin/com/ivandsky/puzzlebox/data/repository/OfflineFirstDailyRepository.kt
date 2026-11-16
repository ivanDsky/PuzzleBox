package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.model.toEntity
import com.ivandsky.puzzlebox.data.model.toPuzzle
import com.ivandsky.puzzlebox.database.PuzzleDailyDao
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cache-first daily for every registered game (A7/A8, ADR-0012). A source may revise the
 * daily before first play: [refreshToday] stores a first fetch and reports a differing one,
 * and only [replaceToday] writes it over the stored row. Callers of [refreshToday] share one request per game and
 * day, and a refresh within [REFRESH_SKIP_WINDOW_MS] of a successful fetch answers from that fetch.
 * A failed refresh stores nothing. A past day is
 * served from cache, and fetched on a miss only when its source declares `archiveSince`:
 * through `fetchPast`
 * before the game's today, `fetchDaily` on it (A5). A past day's row the source calls a
 * placeholder is a miss (A10); a failed refetch is Failed, never the placeholder.
 * Rows share the `puzzle_dailies` store keyed by `gameId`; cached payloads decode through
 * the owning game's definition from [registry].
 *
 * Each game's days come from [calendar] (its cadence zone; null for a game the registry
 * does not know), including whether a row was fetched after its own day (archived).
 */
internal class OfflineFirstDailyRepository(
    private val sources: Map<GameId, DailySource>,
    private val registry: GameRegistry,
    private val dao: PuzzleDailyDao,
    private val dispatchers: DispatcherProvider,
    private val calendar: GameCalendar,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : DailyRepository {

    /** The last successful fetch of a game's day: what the skip window answers from and [replaceToday] may write. */
    private class Fetched(val day: LocalDate, val fetch: DailyFetch, val atMs: Long)

    private val refreshLock = Mutex()
    private val inFlight = HashMap<Pair<GameId, LocalDate>, Deferred<DailyRefresh>>()
    private val lastFetched = ConcurrentHashMap<GameId, Fetched>()

    override suspend fun refreshToday(game: GameId): DailyRefresh {
        val source = sources[game]
        val day = calendar.today(game)
        if (source == null || day == null) {
            return DailyRefresh.Failed(FetchError.Unavailable("no daily source registered for ${game.value}"))
        }
        val key = game to day
        val run = refreshLock.withLock {
            inFlight.getOrPut(key) {
                // On the repository's scope, not the caller's: a caller that leaves must not cancel the others' fetch.
                scope.async(dispatchers.io) {
                    try {
                        refresh(game, source, day)
                    } finally {
                        withContext(NonCancellable) { refreshLock.withLock { inFlight.remove(key) } }
                    }
                }
            }
        }
        return run.await()
    }

    private suspend fun refresh(game: GameId, source: DailySource, day: LocalDate): DailyRefresh {
        val recent = lastFetched[game]?.takeIf { it.day == day && clock() - it.atMs in 0 until REFRESH_SKIP_WINDOW_MS }
        val fetch = recent?.fetch ?: when (val result = source.fetchDaily(day)) {
            is FetchResult.Ok -> result.value.also { lastFetched[game] = Fetched(day, it, clock()) }
            is FetchResult.Err -> return DailyRefresh.Failed(result.error)
        }
        val fresh = fetch.puzzle.toEntity(fetch.ref, clock())
        val stored = dao.compareAndPut(expected = null, fresh = fresh) ?: return DailyRefresh.Stored(fetch.puzzle)
        if (stored == fresh.copy(fetchedAtMs = stored.fetchedAtMs)) return DailyRefresh.Unchanged
        // A row that no longer decodes is not a stored puzzle: nothing could have been played from it.
        val readable = registry[game]?.definition?.let(stored::toPuzzle) != null
        if (!readable && dao.compareAndPut(expected = stored, fresh = fresh) == stored) {
            return DailyRefresh.Stored(fetch.puzzle)
        }
        return DailyRefresh.Changed(fetch.puzzle)
    }

    override suspend fun replaceToday(game: GameId, expected: Puzzle, fresh: Puzzle): Boolean =
        withContext(dispatchers.io) {
            val definition = registry[game]?.definition ?: return@withContext false
            val day = calendar.today(game) ?: return@withContext false
            // The fetch carries the puzzle number the row needs, and proves the source served [fresh].
            val fetched = lastFetched[game]?.takeIf { it.day == day && it.fetch.puzzle == fresh }
                ?: return@withContext false
            val stored = dao.get(game.value, day.toString())?.takeIf { it.toPuzzle(definition) == expected }
                ?: return@withContext false
            dao.compareAndPut(expected = stored, fresh = fresh.toEntity(fetched.fetch.ref, clock())) == stored
        }

    override fun observeHistory(game: GameId): Flow<List<DailyEntry>> =
        dao.observeHistory(game.value).map { rows ->
            rows.map {
                val day = LocalDate.parse(it.day)
                DailyEntry(
                    gameId = game,
                    day = day,
                    puzzleNumber = it.puzzleNumber,
                    variant = it.variant,
                    fetchedAtMs = it.fetchedAtMs,
                    archived = calendar.dayAt(game, it.fetchedAtMs)?.isAfter(day) == true,
                )
            }
        }

    override suspend fun day(game: GameId, day: LocalDate): DailyDay = withContext(dispatchers.io) {
        val definition = registry[game]?.definition ?: return@withContext DailyDay.NotCaptured
        val past = isPast(game, day)
        dao.get(game.value, day.toString())?.let { row ->
            row.toPuzzle(definition)
                ?.takeUnless { past && isPlaceholder(game, it) }
                ?.let { return@withContext DailyDay.Ready(it, row.puzzleNumber) }
        }
        val range = archiveRange(game) ?: return@withContext DailyDay.NotCaptured
        if (day !in range) return@withContext DailyDay.OutOfRange
        val source = sources.getValue(game)
        when (val fetch = if (past) source.fetchPast(day) else source.fetchDaily(day)) {
            is FetchResult.Ok -> {
                dao.upsert(fetch.value.puzzle.toEntity(fetch.value.ref, clock()))
                DailyDay.Ready(fetch.value.puzzle, (fetch.value.ref.key as? PuzzleKey.Daily)?.puzzleNumber)
            }
            is FetchResult.Err -> DailyDay.Failed(fetch.error)
        }
    }

    override fun archiveRange(game: GameId): ClosedRange<LocalDate>? {
        val since = sources[game]?.archiveSince ?: return null
        val today = calendar.today(game) ?: return null
        return since..today
    }

    override fun puzzleNumber(game: GameId, day: LocalDate): Int? = sources[game]?.puzzleNumber(day)

    override fun linkedInGameTypeId(game: GameId): Int? = sources[game]?.linkedInGameTypeId

    override fun linkedInGamePath(game: GameId): String? = sources[game]?.linkedInGamePath

    override suspend fun cachedPuzzle(game: GameId, day: LocalDate): Puzzle? = withContext(dispatchers.io) {
        cachedRow(game, day)?.takeUnless { isPast(game, day) && isPlaceholder(game, it) }
    }

    private fun isPast(game: GameId, day: LocalDate): Boolean = calendar.today(game)?.let(day::isBefore) == true

    private fun isPlaceholder(game: GameId, puzzle: Puzzle): Boolean = sources[game]?.isPlaceholder(puzzle) == true

    private suspend fun cachedRow(game: GameId, day: LocalDate): Puzzle? {
        val definition = registry[game]?.definition ?: return null
        return dao.get(game.value, day.toString())?.toPuzzle(definition)
    }

    companion object {
        /** A refresh this soon after a successful fetch of the same day makes no request. */
        const val REFRESH_SKIP_WINDOW_MS = 10 * 60_000L
    }
}
