package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * One kept week of the player's Friends rank (schema v17), keyed by ([ownerUid], [gameId], [weekStart]).
 * [pointsPerDay] is the owner's own Friends Overall points for that week averaged per played day and [friendCount]
 * is how many friends stood on the boards then; [formatVersion] versions the meaning of those two numbers.
 * [weekStart] is the ISO date of the week's first day. No friend name or id is stored; the table never syncs.
 */
@Entity(tableName = "rank_history", primaryKeys = ["ownerUid", "gameId", "weekStart"])
data class RankHistoryEntity(
    val ownerUid: String,
    val gameId: String,
    val weekStart: String,
    val pointsPerDay: Double,
    val friendCount: Int,
    val formatVersion: Int,
)
