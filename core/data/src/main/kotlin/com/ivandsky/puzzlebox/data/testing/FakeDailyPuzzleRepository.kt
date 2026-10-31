package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.common.result.AppError
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.model.DailyPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Handwritten fake for feature tests: seeded cache, queued refresh outcome, recorded calls. */
class FakeDailyPuzzleRepository : DailyPuzzleRepository {

    val cachedPuzzles = MutableStateFlow<List<DailyPuzzle>>(emptyList())
    val refreshCalls = mutableListOf<Pair<String, String>>()
    var nextRefreshResult: AppResult<DailyPuzzle>? = null

    fun seed(vararg puzzles: DailyPuzzle) {
        cachedPuzzles.value = puzzles.toList()
    }

    override fun observeHistory(gameId: String): Flow<List<DailyPuzzle>> =
        cachedPuzzles.map { list -> list.filter { it.gameId == gameId } }

    override suspend fun cached(gameId: String, day: String): DailyPuzzle? =
        cachedPuzzles.value.firstOrNull { it.gameId == gameId && it.day == day }

    override suspend fun refresh(gameId: String, day: String): AppResult<DailyPuzzle> {
        refreshCalls += gameId to day
        nextRefreshResult?.let { return it }
        return cached(gameId, day)?.let { AppResult.Ok(it) }
            ?: AppResult.Err(
                AppError.Network(IOException("FakeDailyPuzzleRepository: nothing seeded and no result queued")),
            )
    }
}
