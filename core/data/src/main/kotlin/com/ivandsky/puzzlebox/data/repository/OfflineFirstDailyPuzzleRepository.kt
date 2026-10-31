package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.result.AppError
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.db.DailyPuzzleDao
import com.ivandsky.puzzlebox.data.db.toDomain
import com.ivandsky.puzzlebox.data.model.DailyPuzzle
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.network.toDomain
import com.ivandsky.puzzlebox.data.network.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class OfflineFirstDailyPuzzleRepository(
    private val api: PuzzleFeedApi,
    private val dao: DailyPuzzleDao,
    private val dispatchers: DispatcherProvider,
) : DailyPuzzleRepository {

    override suspend fun refresh(gameId: String, day: String): AppResult<DailyPuzzle> =
        withContext(dispatchers.io) {
            try {
                val dto = api.daily(gameId, day)
                dao.upsert(dto.toEntity(fetchedAtMs = System.currentTimeMillis()))
                AppResult.Ok(dto.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                AppResult.Err(AppError.Http(e.code()))
            } catch (e: IOException) {
                AppResult.Err(AppError.Network(e))
            } catch (e: SerializationException) {
                AppResult.Err(AppError.Parsing(e))
            } catch (e: Exception) {
                AppResult.Err(AppError.Unknown(e))
            }
        }

    override fun observeHistory(gameId: String): Flow<List<DailyPuzzle>> =
        dao.observeByGame(gameId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun cached(gameId: String, day: String): DailyPuzzle? =
        dao.get(gameId, day)?.toDomain()
}
