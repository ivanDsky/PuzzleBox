package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * Past you's result for one leg of a self duel, frozen when the duel was created so a later better solve
 * never changes it. Keyed by ([code], [legIndex]); [timeMs] and [hints] are the record's, [completedAtMs] is epoch ms.
 */
@Entity(tableName = "self_past_legs", primaryKeys = ["code", "legIndex"])
data class SelfPastLegEntity(
    val code: String,
    val legIndex: Int,
    val timeMs: Long,
    val hints: Int,
    val completedAtMs: Long,
)
