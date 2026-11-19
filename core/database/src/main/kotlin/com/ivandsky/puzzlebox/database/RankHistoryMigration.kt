package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v17 (insights): adds `rank_history`, empty by default; additive only, no existing table is touched. */
object RankHistoryMigration {

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `rank_history` (" +
                    "`ownerUid` TEXT NOT NULL, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`weekStart` TEXT NOT NULL, " +
                    "`pointsPerDay` REAL NOT NULL, " +
                    "`friendCount` INTEGER NOT NULL, " +
                    "`formatVersion` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`ownerUid`, `gameId`, `weekStart`))",
            )
        }
    }
}
