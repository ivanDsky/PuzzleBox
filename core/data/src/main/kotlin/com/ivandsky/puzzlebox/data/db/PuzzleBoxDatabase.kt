package com.ivandsky.puzzlebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CellsConverter {
    @TypeConverter
    fun encode(cells: List<Int>): String = Json.Default.encodeToString(cells)

    @TypeConverter
    fun decode(json: String): List<Int> = Json.Default.decodeFromString(json)
}

@Database(
    entities = [DailyPuzzleEntity::class, PuzzleDailyEntity::class, CompletionEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(CellsConverter::class)
abstract class PuzzleBoxDatabase : RoomDatabase() {
    abstract fun dailyPuzzleDao(): DailyPuzzleDao

    abstract fun puzzleDailyDao(): PuzzleDailyDao

    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "puzzlebox.db"
    }
}
