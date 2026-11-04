package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleDailyDao
import com.ivandsky.puzzlebox.data.db.toEntity
import com.ivandsky.puzzlebox.data.db.toPuzzle
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Cache-first daily for every registered game (A7/A8): LinkedIn serves only today's
 * puzzle (research 01), so the repository's job is capture-what-you-can and never lose a
 * captured day. Fresh fetches overwrite the row (LinkedIn may revise the daily before
 * first play); failed fetches degrade to the cached row with the sealed error attached.
 * Rows share the `puzzle_dailies` store keyed by `gameId`; cached payloads decode through
 * the owning game's definition from [registry].
 *
 * Each game's "today" is its cadence's current day ([todayProvider]; null for a game the
 * registry does not know).
 */
class OfflineFirstDailyRepository(
    private val sources: Map<GameId, DailySource>,
    private val registry: GameRegistry,
    private val dao: PuzzleDailyDao,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayProvider: (GameId) -> LocalDate? = { registry[it]?.definition?.cadence?.today() },
) : DailyRepository {

    override suspend fun today(game: GameId): DailyToday = withContext(dispatchers.io) {
        val source = sources[game]
        val day = todayProvider(game)
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
        dao.observeByGame(game.value).map { rows ->
            rows.map {
                DailyEntry(
                    gameId = game,
                    day = LocalDate.parse(it.day),
                    puzzleNumber = it.puzzleNumber,
                    variant = it.variant,
                    fetchedAtMs = it.fetchedAtMs,
                )
            }
        }

    private suspend fun cachedRow(game: GameId, day: LocalDate): Puzzle? {
        val definition = registry[game]?.definition ?: return null
        return dao.get(game.value, day.toString())?.toPuzzle(definition)
    }
}
