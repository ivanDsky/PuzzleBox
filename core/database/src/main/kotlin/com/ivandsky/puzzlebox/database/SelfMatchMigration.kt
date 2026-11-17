package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v15 (self duels): adds `self_matches` and `self_past_legs`; additive only, no existing table is touched. */
object SelfMatchMigration {

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `self_matches` (" +
                    "`code` TEXT NOT NULL, " +
                    "`createdAtMs` INTEGER NOT NULL, " +
                    "`roundCount` INTEGER NOT NULL, " +
                    "`games` TEXT NOT NULL, " +
                    "`includeFriends` INTEGER NOT NULL, " +
                    "`legs` TEXT NOT NULL, " +
                    "PRIMARY KEY(`code`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `self_past_legs` (" +
                    "`code` TEXT NOT NULL, " +
                    "`legIndex` INTEGER NOT NULL, " +
                    "`timeMs` INTEGER NOT NULL, " +
                    "`hints` INTEGER NOT NULL, " +
                    "`completedAtMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`code`, `legIndex`))",
            )
        }
    }
}
