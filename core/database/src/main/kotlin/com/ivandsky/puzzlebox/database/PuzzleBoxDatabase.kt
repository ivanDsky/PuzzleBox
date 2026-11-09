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
        LegSavedPlayEntity::class,
        MatchLocalEntity::class,
        MatchResultEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class PuzzleBoxDatabase : RoomDatabase() {
    abstract fun puzzleDailyDao(): PuzzleDailyDao

    abstract fun completionDao(): CompletionDao

    abstract fun savedPlayDao(): SavedPlayDao

    abstract fun customSavedPlayDao(): CustomSavedPlayDao

    abstract fun legSavedPlayDao(): LegSavedPlayDao

    abstract fun draftDao(): DraftDao

    abstract fun publishedPuzzleDao(): PublishedPuzzleDao

    abstract fun receivedPuzzleDao(): ReceivedPuzzleDao

    companion object {
        const val NAME = "puzzlebox.db"
    }
}
