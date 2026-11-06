package com.ivandsky.puzzlebox.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PuzzleDailyEntity::class,
        CompletionEntity::class,
        SavedPlayEntity::class,
        DraftEntity::class,
        PublishedPuzzleEntity::class,
        ReceivedPuzzleEntity::class,
        CustomSavedPlayEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class PuzzleBoxDatabase : RoomDatabase() {
    abstract fun puzzleDailyDao(): PuzzleDailyDao

    abstract fun completionDao(): CompletionDao

    abstract fun savedPlayDao(): SavedPlayDao

    abstract fun customSavedPlayDao(): CustomSavedPlayDao

    companion object {
        const val NAME = "puzzlebox.db"
    }
}
