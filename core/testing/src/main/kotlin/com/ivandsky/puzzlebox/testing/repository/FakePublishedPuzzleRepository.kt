package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PublishedPuzzleRepository]. [rows] exposes every stored row by code for assertions;
 * it passes the same contract test as the Room adapter, so it cannot drift (S15).
 */
class FakePublishedPuzzleRepository : PublishedPuzzleRepository {

    private val state = MutableStateFlow<Map<ShareCode, PublishedPuzzle>>(emptyMap())

    /** Every stored row by code, as last written. */
    val rows: Map<ShareCode, PublishedPuzzle> get() = state.value

    override fun observeAll(): Flow<List<PublishedPuzzle>> =
        state.map { rows -> rows.values.sortedByDescending { it.createdAt } }.distinctUntilChanged()

    override fun observe(code: ShareCode): Flow<PublishedPuzzle?> = state.map { it[code] }.distinctUntilChanged()

    override suspend fun upsert(puzzle: PublishedPuzzle) {
        val stored = puzzle.copy(
            createdAt = puzzle.createdAt.toMillis(),
            solvesCountAt = puzzle.solvesCountAt?.toMillis(),
        )
        state.update { it + (puzzle.code to stored) }
    }

    override suspend fun markUnpublished(code: ShareCode) {
        edit(code) { it.copy(unpublished = true) }
    }

    override suspend fun updateSolves(code: ShareCode, count: Int, at: Instant) {
        edit(code) { it.copy(solvesCount = count, solvesCountAt = at.toMillis()) }
    }

    private fun edit(code: ShareCode, change: (PublishedPuzzle) -> PublishedPuzzle) {
        state.update { rows -> rows[code]?.let { rows + (code to change(it)) } ?: rows }
    }

    private fun Instant.toMillis(): Instant = truncatedTo(ChronoUnit.MILLIS)
}
