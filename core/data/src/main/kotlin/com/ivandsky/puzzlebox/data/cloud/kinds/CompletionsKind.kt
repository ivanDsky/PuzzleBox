package com.ivandsky.puzzlebox.data.cloud.kinds

import androidx.room.withTransaction
import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.repository.FirstWinRecompute
import com.ivandsky.puzzlebox.database.CompletionDao
import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Finished games as a synced kind (ADR-0029 decisions 1 and 8): one doc per UTC month of `completedAtMs`,
 * `{formatVersion: 1, records: {syncId: record}}`, with a numbered overflow doc per 1,000 records of a month. A put
 * deep-merges, so a doc carries only the records not yet uploaded. Only the account's own rows ever leave the device:
 * rows of another owner and ownerless ones are never pending.
 *
 * Restored rows go in through [CompletionDao.insertIgnore], never `CompletionRepository.onCompleted`, so they keep
 * their timestamps and trigger no first-win stamp or live side effect. Right after a restore and after an adopt the
 * record flag of every touched puzzle ref is recomputed inside the account's rows ([FirstWinRecompute]).
 */
internal class CompletionsKind(
    private val db: PuzzleBoxDatabase,
    private val dao: CompletionDao,
    private val merge: CompletionMerge = CompletionMerge(),
    private val firstWin: FirstWinRecompute = FirstWinRecompute(),
) : SyncedKind {

    override val kind: String = "completions"

    override fun pendingIds(uid: String): Flow<Set<String>> =
        dao.observePending(uid).map { rows -> docsOf(uid, rows).keys }.distinctUntilChanged()

    override suspend fun adopt(uid: String) {
        db.withTransaction {
            val refs = dao.ownerlessRefs()
            if (refs.isEmpty()) return@withTransaction
            dao.claimOwnerless(uid)
            recompute(uid, refs)
        }
    }

    override suspend fun pending(uid: String): List<CloudDoc> = db.withTransaction {
        docsOf(uid, dao.pending(uid)).map { (id, rows) ->
            CloudDoc(id, mapOf(FORMAT_VERSION to 1L, CompletionMerge.RECORDS to rows.associate { it.syncId to merge.encode(it) }))
        }
    }

    override suspend fun apply(uid: String, docs: List<CloudDoc>): Int = db.withTransaction {
        val offered = docs.flatMap { (it.fields[CompletionMerge.RECORDS] as? Map<*, *>).orEmpty().keys.filterIsInstance<String>() }
        val known = offered.distinct().chunked(CHUNK).flatMapTo(HashSet()) { dao.existingSyncIds(it) }
        val inserted = merge.rowsToInsert(uid, known, docs).filter { dao.insertIgnore(it) != IGNORED }
        recompute(uid, inserted.map { it.puzzleRef }.distinct())
        inserted.size
    }

    override suspend fun acknowledge(uid: String, uploaded: List<CloudDoc>) {
        val syncIds = uploaded.flatMap { (it.fields[CompletionMerge.RECORDS] as? Map<*, *>).orEmpty().keys.filterIsInstance<String>() }
        db.withTransaction { syncIds.distinct().chunked(CHUNK).forEach { dao.markUploaded(uid, it) } }
    }

    override suspend fun forget(uid: String) = dao.clearUploaded(uid)

    /**
     * [rows] of [uid] grouped by the doc they belong to. A row's doc depends on its place among all of the account's
     * rows of that month, so the month's sync ids are read once per month present in [rows].
     */
    private suspend fun docsOf(uid: String, rows: List<CompletionEntity>): Map<String, List<CompletionEntity>> {
        val docs = LinkedHashMap<String, MutableList<CompletionEntity>>()
        for ((month, inMonth) in rows.groupBy { merge.monthOf(it.completedAtMs) }) {
            val ordinals = dao.syncIdsBetween(uid, merge.startMs(month), merge.startMs(month.plusMonths(1)))
                .withIndex().associate { it.value to it.index }
            for (row in inMonth) {
                val ordinal = ordinals[row.syncId] ?: continue
                docs.getOrPut(merge.docId(row.completedAtMs, ordinal)) { mutableListOf() } += row
            }
        }
        return docs
    }

    /** Fixes the record flag of [refs] among the rows [uid] sees. */
    private suspend fun recompute(uid: String, refs: List<String>) {
        val rows = refs.chunked(CHUNK).flatMap { dao.rowsOfRefs(it, uid) }
        val fixes = firstWin.corrected(rows)
        if (fixes.isNotEmpty()) dao.update(fixes)
    }

    private companion object {
        const val FORMAT_VERSION = "formatVersion"

        /** Ids per SQL statement, under SQLite's bound-variable limit on every supported Android version. */
        const val CHUNK = 500

        /** What an ignored insert returns. */
        const val IGNORED = -1L
    }
}
