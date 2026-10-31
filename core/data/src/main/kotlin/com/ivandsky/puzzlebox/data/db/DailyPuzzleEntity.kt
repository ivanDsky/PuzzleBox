package com.ivandsky.puzzlebox.data.db

import androidx.room.Entity
import com.ivandsky.puzzlebox.data.model.DailyPuzzle

/** gameId + day form the natural key — one cached official daily per game per day. */
@Entity(tableName = "daily_puzzles", primaryKeys = ["gameId", "day"])
data class DailyPuzzleEntity(
    val gameId: String,
    val day: String,
    val size: Int,
    val cells: List<Int>,
    val fetchedAtMs: Long,
)

fun DailyPuzzleEntity.toDomain() = DailyPuzzle(gameId = gameId, day = day, size = size, cells = cells)
