package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleDailyDao
import com.ivandsky.puzzlebox.data.db.toTangoEntity
import com.ivandsky.puzzlebox.data.db.toTangoPuzzle
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Cache-first Tango daily (A7/A8): the [OfflineFirstZipDailyRepository] shape —
 * capture-what-you-can, never lose a captured day. Fresh fetches overwrite the row
 * (LinkedIn may revise the daily before first play); failed fetches degrade to the
 * cached row with the sealed error attached. Rows share the `puzzle_dailies` store
 * keyed by `gameId` — zero Room schema change (A8).
 */
class OfflineFirstTangoDailyRepository(
    private val source: TangoDailySource,
    private val dao: PuzzleDailyDao,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayProvider: () -> LocalDate = { LocalDate.now(tangoCadenceZone()) },
) : TangoDailyRepository {

    override suspend fun today(): TangoToday = withContext(dispatchers.io) {
        val day = todayProvider()
        when (val fetch = source.fetchDaily(day)) {
            is FetchResult.Ok -> {
                dao.upsert(fetch.value.puzzle.toTangoEntity(fetch.value.ref, clock()))
                TangoToday.Fresh(fetch.value.puzzle)
            }
            is FetchResult.Err -> {
                val cached = dao.get(gameId, day.toString())?.toTangoPuzzle()
                if (cached != null) TangoToday.Cached(cached, fetch.error)
                else TangoToday.Unavailable(fetch.error)
            }
        }
    }

    override suspend fun cached(day: LocalDate): Puzzle? = withContext(dispatchers.io) {
        dao.get(gameId, day.toString())?.toTangoPuzzle()
    }

    override fun observeHistory(): Flow<List<TangoDailyEntry>> =
        dao.observeByGame(gameId).map { rows ->
            rows.map {
                TangoDailyEntry(
                    day = LocalDate.parse(it.day),
                    puzzleNumber = it.puzzleNumber,
                    variant = it.variant,
                    fetchedAtMs = it.fetchedAtMs,
                )
            }
        }

    private companion object {
        val gameId = TangoDefinition.id.value
    }
}

/** A10: Tango rolls in LinkedIn's zone; TangoDefinition pins America/Los_Angeles. */
private fun tangoCadenceZone(): ZoneId = (TangoDefinition.cadence as Cadence.Daily).zone
