package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.database.CompletionEntity

/**
 * Pure record flag per puzzle ref (ADR-0029 decision 8), for rows that came in outside the live finish path. Inside one
 * owner set the earliest `completedAtMs` of a ref is its record and the rest are play-agains; a tie goes to the smaller
 * `syncId`. The live path already makes the earliest finish the record, so live and merged orders agree. Like
 * [FirstWinPolicy] it needs no Room, and it never reads or writes leaderboard data.
 */
internal class FirstWinRecompute {

    private val earliest = compareBy<CompletionEntity>({ it.completedAtMs }, { it.syncId })

    /**
     * The rows of [rows], one owner set (any number of refs), whose record stamps are wrong, each already corrected:
     * the winner of a ref carries `isRecord` and a `firstCompletedAtMs` (its own if it had one, else its finish), the
     * others carry neither. Rows that are right are not returned, so a settled set comes back empty.
     */
    fun corrected(rows: List<CompletionEntity>): List<CompletionEntity> =
        rows.groupBy { it.puzzleRef }.values.flatMap { sameRef ->
            val winner = sameRef.minWith(earliest).syncId
            sameRef.mapNotNull { row ->
                val record = row.syncId == winner
                val first = if (record) row.firstCompletedAtMs ?: row.completedAtMs else null
                if (row.isRecord == record && row.firstCompletedAtMs == first) {
                    null
                } else {
                    row.copy(isRecord = record, firstCompletedAtMs = first)
                }
            }
        }
}
