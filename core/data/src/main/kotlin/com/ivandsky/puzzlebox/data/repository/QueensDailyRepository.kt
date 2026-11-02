package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleDailyDao
import com.ivandsky.puzzlebox.data.db.PuzzleDailyEntity
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.feed.QueensDailySource
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * UI-state contract for "today's Queens" (zip ZipToday precedent; A7/A8). [Fresh] is a
 * new official daily; [Cached] is today's previously captured puzzle plus the sealed
 * fetch error (banner state, board still playable); [Unavailable] is nothing yet.
 */
sealed interface QueensToday {
    data class Fresh(val puzzle: Puzzle) : QueensToday
    data class Cached(val puzzle: Puzzle, val error: FetchError) : QueensToday
    data class Unavailable(val error: FetchError) : QueensToday
}

/** History row (zip ZipDailyEntry precedent; Paging3 layers over these later). */
data class QueensDailyEntry(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val variant: String,
    val fetchedAtMs: Long,
)

/**
 * Queens compete loop data (zip ZipDailyRepository precedent): fetch is anonymous
 * LinkedIn (A7); every fetched day is cached in Room; history and replay are cache-only
 * (A8). The A13 gate ran at the source, so every cached row is a proven-unique board.
 */
interface QueensDailyRepository {

    /** Fetch-today → cache; on any fetch failure degrade to today's cache + the sealed error. */
    suspend fun today(): QueensToday

    /** Cache-only read for replay; null when the day wasn't captured. */
    suspend fun cached(day: LocalDate): Puzzle?

    /** Captured days, newest first (A8). Never touches the network. */
    fun observeHistory(): Flow<List<QueensDailyEntry>>
}

/**
 * Cache-first Queens daily (zip OfflineFirstZipDailyRepository precedent): fresh fetches
 * overwrite the row; failed fetches degrade to the cached row with the sealed error.
 */
class OfflineFirstQueensDailyRepository(
    private val source: QueensDailySource,
    private val dao: PuzzleDailyDao,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayProvider: () -> LocalDate = { LocalDate.now(queensCadenceZone()) },
) : QueensDailyRepository {

    override suspend fun today(): QueensToday = withContext(dispatchers.io) {
        val day = todayProvider()
        when (val fetch = source.fetchDaily(day)) {
            is FetchResult.Ok -> {
                dao.upsert(fetch.value.puzzle.toQueensEntity(fetch.value.ref, clock()))
                QueensToday.Fresh(fetch.value.puzzle)
            }
            is FetchResult.Err -> {
                val cached = dao.get(gameId, day.toString())?.toQueensPuzzle()
                if (cached != null) QueensToday.Cached(cached, fetch.error)
                else QueensToday.Unavailable(fetch.error)
            }
        }
    }

    override suspend fun cached(day: LocalDate): Puzzle? = withContext(dispatchers.io) {
        dao.get(gameId, day.toString())?.toQueensPuzzle()
    }

    override fun observeHistory(): Flow<List<QueensDailyEntry>> =
        dao.observeByGame(gameId).map { rows ->
            rows.map {
                QueensDailyEntry(
                    day = LocalDate.parse(it.day),
                    puzzleNumber = it.puzzleNumber,
                    variant = it.variant,
                    fetchedAtMs = it.fetchedAtMs,
                )
            }
        }

    private companion object {
        val gameId = QueensDefinition.id.value
    }
}

/** Queens rolls at midnight PT (zip zone; QueensDefinition pins America/Los_Angeles). */
private fun queensCadenceZone(): ZoneId = (QueensDefinition.cadence as Cadence.Daily).zone

/** Cache read: decode through the owning game's definition — one decode path for every reader. */
fun PuzzleDailyEntity.toQueensPuzzle(): Puzzle? = QueensDefinition.decode(payload).getOrNull()

/** Cache write: identity + envelope in one row (the wire carries no date; ref comes from the fetch day). */
fun Puzzle.toQueensEntity(
    ref: QueensPuzzleRef,
    fetchedAtMs: Long,
): PuzzleDailyEntity = PuzzleDailyEntity(
    gameId = gameId.value,
    day = ref.date.toString(),
    puzzleNumber = ref.puzzleNumber,
    variant = variant.value,
    formatVersion = formatVersion,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)
