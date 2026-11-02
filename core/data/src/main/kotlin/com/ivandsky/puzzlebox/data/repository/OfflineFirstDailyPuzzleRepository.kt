package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.db.DailyPuzzleDao
import com.ivandsky.puzzlebox.data.db.toDomain
import com.ivandsky.puzzlebox.data.network.DailyPuzzleDto
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.network.networkResultOf
import com.ivandsky.puzzlebox.data.network.toDomain
import com.ivandsky.puzzlebox.data.network.toEntity
import com.ivandsky.puzzlebox.model.DailyPuzzle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class OfflineFirstDailyPuzzleRepository(
    private val api: PuzzleFeedApi,
    private val dao: DailyPuzzleDao,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : DailyPuzzleRepository {

    override suspend fun refresh(gameId: String, day: String): AppResult<DailyPuzzle> =
        withContext(dispatchers.io) {
            networkResultOf {
                val payload = api.daily(gameId, day).bytes()
                val dto = json.decodeFromString<DailyPuzzleDto>(payload.decodeToString())
                val fetchedAtMs = System.currentTimeMillis()
                dao.upsert(dto.toEntity(payload = payload, fetchedAtMs = fetchedAtMs))
                dto.toDomain(payload = payload, fetchedAtMs = fetchedAtMs)
            }
        }

    override fun observeHistory(gameId: String): Flow<List<DailyPuzzle>> =
        dao.observeByGame(gameId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun cached(gameId: String, day: String): DailyPuzzle? =
        dao.get(gameId, day)?.toDomain()
}
