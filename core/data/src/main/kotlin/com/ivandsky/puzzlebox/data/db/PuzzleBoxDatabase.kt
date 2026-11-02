package com.ivandsky.puzzlebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyPuzzleEntity::class, PuzzleDailyEntity::class, CompletionEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class PuzzleBoxDatabase : RoomDatabase() {
    abstract fun dailyPuzzleDao(): DailyPuzzleDao

    abstract fun puzzleDailyDao(): PuzzleDailyDao

    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "puzzlebox.db"
    }
}
