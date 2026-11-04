package com.ivandsky.puzzlebox.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PuzzleDailyEntity::class, CompletionEntity::class, SavedPlayEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class PuzzleBoxDatabase : RoomDatabase() {
    abstract fun puzzleDailyDao(): PuzzleDailyDao

    abstract fun completionDao(): CompletionDao

    abstract fun savedPlayDao(): SavedPlayDao

    companion object {
        const val NAME = "puzzlebox.db"
    }
}
