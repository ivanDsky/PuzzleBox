package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleDailyDao
import com.ivandsky.puzzlebox.data.db.toEntity
import com.ivandsky.puzzlebox.data.db.toPuzzle
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Cache-first Zip daily (A7/A8): LinkedIn serves only today's puzzle (research 01), so
 * the repository's job is capture-what-you-can and never lose a captured day. Fresh
 * fetches overwrite the row (LinkedIn may revise the daily before first play); failed
 * fetches degrade to the cached row with the sealed error attached.
 */
class OfflineFirstZipDailyRepository(
    private val source: ZipDailySource,
    private val dao: PuzzleDailyDao,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayProvider: () -> LocalDate = { LocalDate.now(zipCadenceZone()) },
) : ZipDailyRepository {

    override suspend fun today(): ZipToday = withContext(dispatchers.io) {
        val day = todayProvider()
        when (val fetch = source.fetchDaily(day)) {
            is FetchResult.Ok -> {
                dao.upsert(fetch.value.puzzle.toEntity(fetch.value.ref, clock()))
                ZipToday.Fresh(fetch.value.puzzle)
            }
            is FetchResult.Err -> {
                val cached = dao.get(gameId, day.toString())?.toPuzzle()
                if (cached != null) ZipToday.Cached(cached, fetch.error)
                else ZipToday.Unavailable(fetch.error)
            }
        }
    }

    override suspend fun cached(day: LocalDate): Puzzle? = withContext(dispatchers.io) {
        dao.get(gameId, day.toString())?.toPuzzle()
    }

    override fun observeHistory(): Flow<List<ZipDailyEntry>> =
        dao.observeByGame(gameId).map { rows ->
            rows.map {
                ZipDailyEntry(
                    day = LocalDate.parse(it.day),
                    puzzleNumber = it.puzzleNumber,
                    variant = it.variant,
                    fetchedAtMs = it.fetchedAtMs,
                )
            }
        }

    private companion object {
        val gameId = ZipDefinition.id.value
    }
}

/** Zip rolls at midnight PT (research 01; ZipDefinition pins America/Los_Angeles). */
private fun zipCadenceZone(): ZoneId = (ZipDefinition.cadence as Cadence.Daily).zone
