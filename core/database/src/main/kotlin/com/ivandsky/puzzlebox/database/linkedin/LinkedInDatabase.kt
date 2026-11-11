package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Everything the app keeps about LinkedIn connections and about what it has already asked
 * LinkedIn. A file of its own, apart from the main database, because backup and device
 * transfer leave out whole files, not tables. It holds no session: no cookie or token
 * column exists in any table.
 */
@Database(
    entities = [GhostEntity::class, FetchLogEntity::class, SavedBoardEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class LinkedInDatabase : RoomDatabase() {
    abstract fun ghostDao(): GhostDao

    abstract fun fetchLogDao(): FetchLogDao

    abstract fun savedBoardDao(): SavedBoardDao

    companion object {
        const val NAME = "linkedin.db"
    }
}
