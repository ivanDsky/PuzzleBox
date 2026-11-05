package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.model.toEntity
import com.ivandsky.puzzlebox.data.model.toPuzzle
import com.ivandsky.puzzlebox.database.PuzzleDailyDao
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Cache-first daily for every registered game (A7/A8, ADR-0012). Fresh fetches overwrite
 * the row (a source may revise the daily before first play); failed fetches degrade to
 * the cached row with the sealed error attached. A past day is served from cache, and
 * fetched on a miss only when its source declares `archiveSince` — the LinkedIn feeds
 * ignore the date, so fetching a past day there would store today's puzzle under it.
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
    private val clock: () -> Long = System::currentTimeMillis,
) : DailyRepository {

    override suspend fun today(game: GameId): DailyToday = withContext(dispatchers.io) {
        val source = sources[game]
        val day = calendar.today(game)
        if (source == null || day == null) {
            return@withContext DailyToday.Unavailable(
                FetchError.Unavailable("no daily source registered for ${game.value}"),
            )
        }
        when (val fetch = source.fetchDaily(day)) {
            is FetchResult.Ok -> {
                dao.upsert(fetch.value.puzzle.toEntity(fetch.value.ref, clock()))
                DailyToday.Fresh(fetch.value.puzzle)
            }
            is FetchResult.Err -> {
                val cached = cachedRow(game, day)
                if (cached != null) DailyToday.Cached(cached, fetch.error)
                else DailyToday.Unavailable(fetch.error)
            }
        }
    }

    override suspend fun cached(game: GameId, day: LocalDate): Puzzle? = withContext(dispatchers.io) {
        cachedRow(game, day)
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
        dao.get(game.value, day.toString())?.let { row ->
            row.toPuzzle(definition)?.let { return@withContext DailyDay.Ready(it, row.puzzleNumber) }
        }
        val range = archiveRange(game) ?: return@withContext DailyDay.NotCaptured
        if (day !in range) return@withContext DailyDay.OutOfRange
        when (val fetch = sources.getValue(game).fetchDaily(day)) {
            is FetchResult.Ok -> {
                dao.upsert(fetch.value.puzzle.toEntity(fetch.value.ref, clock()))
                DailyDay.Ready(fetch.value.puzzle, fetch.value.ref.puzzleNumber)
            }
            is FetchResult.Err -> DailyDay.Failed(fetch.error)
        }
    }

    override fun archiveRange(game: GameId): ClosedRange<LocalDate>? {
        val since = sources[game]?.archiveSince ?: return null
        val today = calendar.today(game) ?: return null
        return since..today
    }

    private suspend fun cachedRow(game: GameId, day: LocalDate): Puzzle? {
        val definition = registry[game]?.definition ?: return null
        return dao.get(game.value, day.toString())?.toPuzzle(definition)
    }
}
