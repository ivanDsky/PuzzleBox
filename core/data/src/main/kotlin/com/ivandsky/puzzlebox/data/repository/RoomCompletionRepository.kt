package com.ivandsky.puzzlebox.data.repository

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.model.toModel
import com.ivandsky.puzzlebox.database.CompletionDao
import com.ivandsky.puzzlebox.database.CompletionReplayDao
import com.ivandsky.puzzlebox.database.CompletionReplayEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Room implementation of the pinned [CompletionRepository] contract. The record-exists
 * check and the insert run in one transaction so two racing completions of the same ref
 * cannot both stamp isRecord (A4). Rows whose key no game in [registry] can decode are
 * dropped on read (A19 preserve-or-drop), never reinterpreted. Reads show the [owner]'s visible rows (re-querying when
 * it changes) and new rows carry its writer uid; the first win is decided inside the visible set (ADR-0029).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RoomCompletionRepository(
    private val db: PuzzleBoxDatabase,
    private val dao: CompletionDao,
    private val replayDao: CompletionReplayDao,
    private val registry: GameRegistry,
    private val owner: OwnerScope,
    private val policy: FirstWinPolicy = FirstWinPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) : CompletionRepository {

    override suspend fun onCompleted(record: CompletionRecord, replay: SolveReplay?) {
        val refKey = PuzzleRefKeys.encode(record.puzzleRef)
        val visible = owner.visibleNow()
        db.withTransaction {
            // A repeated import adds nothing; checking inside the transaction keeps two racing imports from both inserting.
            if (record.source == CompletionSource.LinkedIn &&
                dao.hasSource(refKey, CompletionSource.LinkedIn.storedName, visible)
            ) {
                return@withTransaction
            }
            val decision = policy.decide(hasRecord = dao.record(refKey, visible) != null, nowMs = clock())
            val completionId = dao.insert(policy.stamp(record, refKey, decision, owner.writer()))
            // The completion row goes first: the replay row references it.
            if (replay != null) {
                replayDao.insert(CompletionReplayEntity(completionId, replay.toByteArray()))
                replayDao.prune(refKey, visible)
            }
        }
    }

    override fun observeRecords(): Flow<List<CompletionRecord>> =
        owner.visible.flatMapLatest(dao::observeRecords).map { rows -> rows.mapNotNull { it.toModel(registry) } }

    override fun observeImports(): Flow<List<CompletionRecord>> =
        owner.visible.flatMapLatest { dao.observeBySource(CompletionSource.LinkedIn.storedName, it) }.map { rows ->
            rows.mapNotNull { it.toModel(registry) }
        }

    override fun observePlayAgains(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> {
        val refKey = PuzzleRefKeys.encode(puzzleRef)
        return owner.visible.flatMapLatest { dao.observePlayAgains(refKey, it) }.map { rows ->
            rows.mapNotNull { it.toModel(registry) }
        }
    }

    // Same key shape PuzzleRefKeys.encode writes for a PuzzleKey.Custom ref.
    override fun observeCustom(gameId: GameId, puzzleId: String): Flow<List<CompletionRecord>> =
        owner.visible.flatMapLatest {
            dao.observeByRef("${PuzzleKey.Custom.NAMESPACE}/${gameId.value}/$puzzleId", it)
        }.map { rows -> rows.mapNotNull { it.toModel(registry) } }

    // PuzzleRefKeys.encode leads every daily key with "$gameId/"; a custom key leads with
    // "custom/", so the prefix keeps friends' puzzles out of the daily best (ADR-0018).
    // A game this build does not ship ranks time-first, the shared default.
    override suspend fun best(game: GameId): BestScore? {
        val prefix = "${game.value}/"
        val visible = owner.visibleNow()
        val row = when (registry[game]?.score?.rankOrder ?: RankOrder.TimeThenHints) {
            RankOrder.TimeThenHints -> dao.bestByTime(prefix, visible)
            RankOrder.HintsThenTime -> dao.bestByHints(prefix, visible)
        }
        return row?.let { BestScore(it.timeMs, it.hints) }
    }

    // An unreadable size (empty or over the cap) reads as no replay.
    override suspend fun recordReplay(puzzleRef: PuzzleRef): SolveReplay? =
        replayDao.recordReplay(PuzzleRefKeys.encode(puzzleRef), owner.visibleNow())?.let(SolveReplay::of)

    override suspend fun latestReplay(puzzleRef: PuzzleRef): SolveReplay? =
        replayDao.latestReplay(PuzzleRefKeys.encode(puzzleRef), owner.visibleNow())?.let(SolveReplay::of)

    override suspend fun latestIsRecord(puzzleRef: PuzzleRef): Boolean =
        replayDao.latestIsRecord(PuzzleRefKeys.encode(puzzleRef), owner.visibleNow())
}
