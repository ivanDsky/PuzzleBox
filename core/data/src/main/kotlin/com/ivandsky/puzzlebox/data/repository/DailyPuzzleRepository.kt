package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.model.DailyPuzzle
import kotlinx.coroutines.flow.Flow

/**
 * Exemplar repository contract — interfaces live in core; features see this only.
 * Convention: refresh() is network-first and returns the fetch outcome; observeHistory()
 * and cached() never touch the network, so callers degrade to cache by observing them.
 */
interface DailyPuzzleRepository {
    suspend fun refresh(gameId: String, day: String): AppResult<DailyPuzzle>

    fun observeHistory(gameId: String): Flow<List<DailyPuzzle>>

    suspend fun cached(gameId: String, day: String): DailyPuzzle?
}
