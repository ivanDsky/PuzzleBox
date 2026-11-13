package com.ivandsky.puzzlebox.database.finalboard

import androidx.room.Entity

/**
 * Header of one stored Final board of [gameId] on [day] (an ISO date). [count] is the board's size as
 * the cloud reported it; 0 is a valid stored empty board, so a missing header means "never fetched".
 */
@Entity(tableName = "final_boards", primaryKeys = ["gameId", "day"])
data class FinalBoardEntity(
    val gameId: String,
    val day: String,
    val count: Int,
    /** When the board was fetched (epoch ms). */
    val fetchedAtMs: Long,
)
