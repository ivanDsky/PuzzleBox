package com.ivandsky.puzzlebox.data.cloud.kinds

import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.CompletionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Pure side of the completions kind (ADR-0029 decisions 3 and 8): how a row becomes a cloud record and which cloud
 * records become local rows. A completion is immutable once finished, so the merge is by `syncId` alone: a record the
 * device already holds is left as it is and every other one is inserted with the cloud's timestamps and the account as
 * owner. The record flag is never synced; the caller recomputes it after inserting (`FirstWinRecompute`).
 */
internal class CompletionMerge {

    /** The cloud record of [row]: its nine fields, never `isRecord`; numbers are `Long`, the only integer cloud type. */
    fun encode(row: CompletionEntity): Map<String, Any?> = mapOf(
        PUZZLE_REF to row.puzzleRef,
        TIME_MS to row.timeMs,
        HINTS to row.hints.toLong(),
        STATS_VERSION to row.statsVersion.toLong(),
        STATS_BLOB to row.statsBlob,
        COMPLETED_AT_MS to row.completedAtMs,
        FIRST_COMPLETED_AT_MS to row.firstCompletedAtMs,
        MATCH_CODE to row.matchCode,
        SOURCE to row.source,
    )

    /** The UTC month of [completedAtMs]. */
    fun monthOf(completedAtMs: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(completedAtMs).atZone(ZoneOffset.UTC))

    /** The first instant of [month], UTC, in epoch milliseconds. */
    fun startMs(month: YearMonth): Long = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * The doc a record finished at [completedAtMs] lives in: its UTC month `yyyy-MM`, and `yyyy-MM-n` for the n-th
     * block of [PER_DOC] records once the month holds more, where [ordinal] is the record's place among the month's
     * records ordered by (`completedAtMs`, `syncId`), counted from 0. The first block keeps the plain month id.
     */
    fun docId(completedAtMs: Long, ordinal: Int): String {
        val month = monthOf(completedAtMs)
        val id = "${month.year.toString().padStart(4, '0')}-${month.monthValue.toString().padStart(2, '0')}"
        val block = ordinal / PER_DOC
        return if (block == 0) id else "$id-$block"
    }

    /**
     * The rows to insert for [uid] from [docs]: the records whose sync id is not in [known], once each however many
     * docs carry them, oldest finish first. Records that cannot be read are skipped. Merging the same docs again with
     * the inserted ids added to [known] returns nothing, and the result does not depend on the order of [docs].
     */
    fun rowsToInsert(uid: String, known: Set<String>, docs: List<CloudDoc>): List<CompletionEntity> =
        docs.sortedBy { it.id }
            .flatMap { doc -> (doc.fields[RECORDS] as? Map<*, *>).orEmpty().entries }
            .mapNotNull { (key, value) -> (key as? String)?.takeIf { it.isNotEmpty() && it !in known }?.let { it to value } }
            .distinctBy { it.first }
            .mapNotNull { (syncId, value) -> decode(uid, syncId, value) }
            .sortedWith(compareBy({ it.completedAtMs }, { it.syncId }))

    private fun decode(uid: String, syncId: String, value: Any?): CompletionEntity? {
        val record = value as? Map<*, *> ?: return null
        return CompletionEntity(
            puzzleRef = record[PUZZLE_REF] as? String ?: return null,
            timeMs = (record[TIME_MS] as? Number)?.toLong() ?: return null,
            hints = (record[HINTS] as? Number)?.toInt() ?: return null,
            statsVersion = (record[STATS_VERSION] as? Number)?.toInt() ?: return null,
            statsBlob = record[STATS_BLOB] as? String ?: return null,
            completedAtMs = (record[COMPLETED_AT_MS] as? Number)?.toLong() ?: return null,
            firstCompletedAtMs = (record[FIRST_COMPLETED_AT_MS] as? Number)?.toLong(),
            isRecord = false,
            matchCode = record[MATCH_CODE] as? String,
            source = record[SOURCE] as? String ?: DEFAULT_SOURCE,
            ownerUid = uid,
            syncId = syncId,
            uploaded = true,
        )
    }

    internal companion object {
        /** The key of the records map in a doc. */
        const val RECORDS = "records"

        /** The most records one doc holds; the rules cap it at 1,000. */
        const val PER_DOC = 1_000

        private const val PUZZLE_REF = "puzzleRef"
        private const val TIME_MS = "timeMs"
        private const val HINTS = "hints"
        private const val STATS_VERSION = "statsVersion"
        private const val STATS_BLOB = "statsBlob"
        private const val COMPLETED_AT_MS = "completedAtMs"
        private const val FIRST_COMPLETED_AT_MS = "firstCompletedAtMs"
        private const val MATCH_CODE = "matchCode"
        private const val SOURCE = "source"
        private const val DEFAULT_SOURCE = "app"
    }
}
