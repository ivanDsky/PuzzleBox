package com.ivandsky.puzzlebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyPuzzleEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PuzzleBoxDatabase : RoomDatabase() {
    abstract fun dailyPuzzleDao(): DailyPuzzleDao

    companion object {
        const val NAME = "puzzlebox.db"
    }
}
