package com.ivandsky.puzzlebox.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The Trainer's own puzzle cache: a puzzle is cached forever, so the first fetch of a (game, number) stays. */
@Dao
interface TrainerPuzzleDao {

    /** The cached puzzle of [gameId] numbered [number], or null when the Trainer never fetched it. */
    @Query("SELECT * FROM trainer_puzzles WHERE gameId = :gameId AND puzzleNumber = :number")
    suspend fun get(gameId: String, number: Int): TrainerPuzzleEntity?

    /** Keeps [puzzle] unless its (gameId, puzzleNumber) is cached; returns the new row id, or -1 when it was. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(puzzle: TrainerPuzzleEntity): Long

    /** The puzzle numbers cached for [gameId], lowest first. */
    @Query("SELECT puzzleNumber FROM trainer_puzzles WHERE gameId = :gameId ORDER BY puzzleNumber")
    suspend fun numbers(gameId: String): List<Int>
}
