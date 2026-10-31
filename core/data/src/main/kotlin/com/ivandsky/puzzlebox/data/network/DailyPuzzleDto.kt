package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.data.db.DailyPuzzleEntity
import com.ivandsky.puzzlebox.data.model.DailyPuzzle
import kotlinx.serialization.Serializable

@Serializable
data class DailyPuzzleDto(
    val gameId: String,
    val day: String,
    val size: Int,
    val cells: List<Int> = emptyList(),
    val solution: List<Int> = emptyList(),
)

fun DailyPuzzleDto.toDomain() = DailyPuzzle(gameId = gameId, day = day, size = size, cells = cells)

fun DailyPuzzleDto.toEntity(fetchedAtMs: Long) = DailyPuzzleEntity(
    gameId = gameId,
    day = day,
    size = size,
    cells = cells,
    fetchedAtMs = fetchedAtMs,
)
