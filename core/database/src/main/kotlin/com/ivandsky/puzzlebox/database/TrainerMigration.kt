package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v18 (Trainer): adds `trainer_progress` and `trainer_puzzles`, both empty, so every technique starts new for an
 * existing player; additive only, no existing table is touched and no row is written.
 */
object TrainerMigration {

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trainer_progress` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`ownerUid` TEXT, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`techniqueId` TEXT NOT NULL, " +
                    "`mark` TEXT NOT NULL, " +
                    "`inRow` INTEGER NOT NULL, " +
                    "`answeredAtMs` INTEGER NOT NULL, " +
                    "`uploaded` INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_trainer_progress_gameId_ownerUid` " +
                    "ON `trainer_progress` (`gameId`, `ownerUid`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trainer_puzzles` (" +
                    "`gameId` TEXT NOT NULL, " +
                    "`puzzleNumber` INTEGER NOT NULL, " +
                    "`day` TEXT NOT NULL, " +
                    "`variant` TEXT NOT NULL, " +
                    "`formatVersion` INTEGER NOT NULL, " +
                    "`payload` BLOB NOT NULL, " +
                    "`fetchedAtMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`gameId`, `puzzleNumber`))",
            )
        }
    }
}
