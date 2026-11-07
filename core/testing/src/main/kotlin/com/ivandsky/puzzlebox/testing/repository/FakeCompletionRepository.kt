package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CompletionRepository]: stores verbatim what callers write (A4 stamping lives
 * in the Room implementation) and re-emits the whole list on every change, like the real
 * DAO flow. [fail] drives the write-failure path; [writeGate] suspends writes until it
 * completes; [setRecords] replaces the stored rows. [rankOrders] stands in for the registry's
 * per-game order; a game missing from it ranks time-first, as in Room.
 */
class FakeCompletionRepository(
    records: List<CompletionRecord> = emptyList(),
    private val rankOrders: Map<GameId, RankOrder> = emptyMap(),
) : CompletionRepository {

    var fail = false

    /** When set, every write suspends until it completes (an in-flight write). */
    var writeGate: CompletableDeferred<Unit>? = null

    private val all = MutableStateFlow(records)

    /** Replaces every stored row; observers see one new emission. */
    fun setRecords(records: List<CompletionRecord>) {
        all.value = records
    }

    override suspend fun onCompleted(record: CompletionRecord) {
        writeGate?.await()
        if (fail) throw IllegalStateException("disk full")
        all.update { it + record }
    }

    override fun observeRecords(): Flow<List<CompletionRecord>> = all

    override fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
        all.map { rows -> rows.filter { it.puzzleRef == puzzleRef } }

    /**
     * Rows are verbatim, so the record is each identity's FIRST stored row (A4 stamping).
     * Same `"$gameId/"` key-prefix rule as Room, so a custom (`custom/…`) solve never ranks.
     */
    override suspend fun best(game: GameId): BestScore? {
        val ranking = (rankOrders[game] ?: RankOrder.TimeThenHints).comparator
        val prefix = "${game.value}/"
        return all.value.groupBy { PuzzleRefKeys.encode(it.puzzleRef) }
            .filterKeys { it.startsWith(prefix) }
            .values
            .map { rows -> rows.first().let { BestScore(it.time, it.hints) } }
            .minWithOrNull(ranking)
    }
}
