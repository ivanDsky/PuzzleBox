package com.ivandsky.puzzlebox.database.finalboard

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Cache of Final boards read from the cloud. A file of its own, apart from the main database, because
 * backup and device transfer leave out whole files, not tables, and it holds other players' names.
 * Nothing here is authoritative: a version change drops the tables and the boards are fetched again.
 */
@Database(
    entities = [FinalBoardEntity::class, FinalBoardEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FinalBoardDatabase : RoomDatabase() {
    abstract fun finalBoardDao(): FinalBoardDao

    companion object {
        const val NAME = "final_boards.db"
    }
}
