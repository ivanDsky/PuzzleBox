package com.ivandsky.puzzlebox.data.repository

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.model.toModel
import com.ivandsky.puzzlebox.database.CompletionDao
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
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
            // A repeated import adds nothing; checking inside the transaction keeps two racing imports from both inserting.
            if (record.source == CompletionSource.LinkedIn &&
                dao.hasSource(refKey, CompletionSource.LinkedIn.storedName)
            ) {
                return@withTransaction
            }
            val decision = policy.decide(hasRecord = dao.record(refKey) != null, nowMs = clock())
            dao.insert(policy.stamp(record, refKey, decision))
        }
    }

    override fun observeRecords(): Flow<List<CompletionRecord>> =
        dao.observeRecords().map { rows -> rows.mapNotNull { it.toModel(registry) } }

    override fun observeImports(): Flow<List<CompletionRecord>> =
        dao.observeBySource(CompletionSource.LinkedIn.storedName).map { rows ->
            rows.mapNotNull { it.toModel(registry) }
        }

    override fun observePlayAgains(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
        dao.observePlayAgains(PuzzleRefKeys.encode(puzzleRef)).map { rows ->
            rows.mapNotNull { it.toModel(registry) }
        }

    // Same key shape PuzzleRefKeys.encode writes for a PuzzleKey.Custom ref.
    override fun observeCustom(gameId: GameId, puzzleId: String): Flow<List<CompletionRecord>> =
        dao.observeByRef("${PuzzleKey.Custom.NAMESPACE}/${gameId.value}/$puzzleId").map { rows ->
            rows.mapNotNull { it.toModel(registry) }
        }

    // PuzzleRefKeys.encode leads every daily key with "$gameId/"; a custom key leads with
    // "custom/", so the prefix keeps friends' puzzles out of the daily best (ADR-0018).
    // A game this build does not ship ranks time-first, the shared default.
    override suspend fun best(game: GameId): BestScore? {
        val prefix = "${game.value}/"
        val row = when (registry[game]?.score?.rankOrder ?: RankOrder.TimeThenHints) {
            RankOrder.TimeThenHints -> dao.bestByTime(prefix)
            RankOrder.HintsThenTime -> dao.bestByHints(prefix)
        }
        return row?.let { BestScore(it.timeMs, it.hints) }
    }
}
