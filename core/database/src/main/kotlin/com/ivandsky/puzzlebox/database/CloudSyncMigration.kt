package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v11 (cloud sync, ADR-0029 §7): additive only, every v10 row is kept.
 * - `completions` gains `ownerUid` (null), `syncId` (a random 32-hex id backfilled per row, unique-indexed)
 *   and `uploaded` (0);
 * - `drafts`, `published_puzzles` and `received_puzzles` gain nullable `ownerUid` and `uploadedFingerprint`;
 * - the new table `sync_tombstones` holds pending cloud deletes.
 */
object CloudSyncMigration {

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `completions` ADD COLUMN `ownerUid` TEXT")
            // SQLite needs a default to add a NOT NULL column; the backfill below replaces it per row.
            db.execSQL("ALTER TABLE `completions` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `completions` SET `syncId` = lower(hex(randomblob(16)))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_completions_syncId` ON `completions` (`syncId`)")
            db.execSQL("ALTER TABLE `completions` ADD COLUMN `uploaded` INTEGER NOT NULL DEFAULT 0")
            for (table in listOf("drafts", "published_puzzles", "received_puzzles")) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `ownerUid` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `uploadedFingerprint` TEXT")
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_tombstones` (" +
                    "`kind` TEXT NOT NULL, " +
                    "`docId` TEXT NOT NULL, " +
                    "`ownerUid` TEXT NOT NULL, " +
                    "`deletedAtMs` INTEGER NOT NULL, " +
                    "`eligibleAtMs` INTEGER NOT NULL, " +
                    "`uploaded` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`kind`, `docId`, `ownerUid`))",
            )
        }
    }
}
