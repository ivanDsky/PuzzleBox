package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ReceivedPuzzleRepository]. [rows] exposes every stored row by code for assertions; it
 * passes the same contract test as the Room adapter, so it cannot drift (S15). Puzzle bytes are
 * copied in and out, so callers can never mutate a stored row.
 */
class FakeReceivedPuzzleRepository : ReceivedPuzzleRepository {

    private val store = MutableStateFlow<Map<ShareCode, ReceivedPuzzle>>(emptyMap())

    /** Every stored row by code, as last written. */
    val rows: Map<ShareCode, ReceivedPuzzle> get() = store.value

    override fun observeAll(): Flow<List<ReceivedPuzzle>> =
        store.map { rows -> rows.values.sortedByDescending { it.openedAt }.map { it.detached() } }.distinctUntilChanged()

    override fun observe(code: ShareCode): Flow<ReceivedPuzzle?> = store.map { it[code]?.detached() }.distinctUntilChanged()

    override suspend fun get(code: ShareCode): ReceivedPuzzle? = store.value[code]?.detached()

    override suspend fun upsert(puzzle: ReceivedPuzzle) {
        val stored = puzzle.copy(openedAt = puzzle.openedAt.truncatedTo(ChronoUnit.MILLIS)).detached()
        store.update { it + (puzzle.code to stored) }
    }

    override suspend fun delete(code: ShareCode) {
        store.update { it - code }
    }

    override suspend fun updatePost(code: ShareCode, state: PostState, attempts: Int) {
        edit(code) { it.copy(postState = state, postAttempts = attempts) }
    }

    override suspend fun markUnpublished(code: ShareCode) {
        edit(code) { it.copy(unpublished = true) }
    }

    private fun edit(code: ShareCode, change: (ReceivedPuzzle) -> ReceivedPuzzle) {
        store.update { rows -> rows[code]?.let { rows + (code to change(it)) } ?: rows }
    }

    private fun ReceivedPuzzle.detached() = copy(puzzleBytes = puzzleBytes.copyOf())
}
