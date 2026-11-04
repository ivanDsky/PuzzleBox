package com.ivandsky.puzzlebox.data.repository

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.db.CompletionDao
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.db.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.db.toModel
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of the pinned [CompletionRepository] contract. The record-exists
 * check and the insert run in one transaction so two racing completions of the same ref
 * cannot both stamp isRecord (A4). Rows whose key no game in [registry] can decode are
 * dropped on read (A19 preserve-or-drop), never reinterpreted.
 */
internal class RoomCompletionRepository(
    private val db: PuzzleBoxDatabase,
    private val dao: CompletionDao,
    private val registry: GameRegistry,
    private val policy: FirstWinPolicy = FirstWinPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) : CompletionRepository {

    override suspend fun onCompleted(record: CompletionRecord) {
        val refKey = PuzzleRefKeys.encode(record.puzzleRef)
        db.withTransaction {
            val decision = policy.decide(hasRecord = dao.record(refKey) != null, nowMs = clock())
            dao.insert(policy.stamp(record, refKey, decision))
        }
    }

    override fun observeRecords(): Flow<List<CompletionRecord>> =
        dao.observeRecords().map { rows -> rows.mapNotNull { it.toModel(registry) } }

    override fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
        dao.observeReplays(PuzzleRefKeys.encode(puzzleRef)).map { rows ->
            rows.mapNotNull { it.toModel(registry) }
        }
}
