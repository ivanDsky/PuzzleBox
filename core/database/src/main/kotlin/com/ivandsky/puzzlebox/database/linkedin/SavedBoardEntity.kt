package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Entity

/**
 * One saved LinkedIn board: the player's own record of which LinkedIn profile read a game day, when, and
 * whether the day was over. Its ghost rows stay in `ghosts` and belong to the board through (game, day);
 * no foreign key, so a board can outlive its ghosts' prune and a restore can write either side first.
 * [day] is an ISO date, so days compare as text.
 */
@Entity(tableName = "saved_boards", primaryKeys = ["game", "day"])
data class SavedBoardEntity(
    val game: String,
    val day: String,
    /** The LinkedIn profile whose session read the board. */
    val profileId: String,
    /** The PuzzleBox account the board belongs to; null when it was read while signed out. */
    val ownerUid: String?,
    /** When the board was read (epoch ms). */
    val readAtMs: Long,
    /** True when the read happened after the day's end, so the board is final. */
    val complete: Boolean,
    /** The fingerprint of the content last acknowledged by the cloud; null while an upload is pending. */
    val uploadedFingerprint: String?,
)
