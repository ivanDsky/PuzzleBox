package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CompletionRepository]: stores verbatim what callers write (A4 stamping lives
 * in the Room implementation) and re-emits the whole list on every change, like the real
 * DAO flow. [fail] drives the write-failure path; [setRecords] replaces the stored rows.
 */
class FakeCompletionRepository(records: List<CompletionRecord> = emptyList()) : CompletionRepository {

    var fail = false

    private val all = MutableStateFlow(records)

    /** Replaces every stored row; observers see one new emission. */
    fun setRecords(records: List<CompletionRecord>) {
        all.value = records
    }

    override suspend fun onCompleted(record: CompletionRecord) {
        if (fail) throw IllegalStateException("disk full")
        all.update { it + record }
    }

    override fun observeRecords(): Flow<List<CompletionRecord>> = all

    override fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
        all.map { rows -> rows.filter { it.puzzleRef == puzzleRef } }
}
