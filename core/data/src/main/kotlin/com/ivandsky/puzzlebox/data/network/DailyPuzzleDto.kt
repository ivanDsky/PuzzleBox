package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.data.db.DailyPuzzleEntity
import com.ivandsky.puzzlebox.model.DailyPuzzle
import kotlinx.serialization.Serializable

/** Network shape of one daily. Kept verbatim — the envelope layer does not interpret it. */
@Serializable
data class DailyPuzzleDto(
    val gameId: String,
    val day: String,
    val size: Int,
    val cells: List<Int> = emptyList(),
    val solution: List<Int> = emptyList(),
)

fun DailyPuzzleDto.toDomain(payload: ByteArray, fetchedAtMs: Long) = DailyPuzzle(
    gameId = gameId,
    day = day,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)

fun DailyPuzzleDto.toEntity(payload: ByteArray, fetchedAtMs: Long) = DailyPuzzleEntity(
    gameId = gameId,
    day = day,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)
