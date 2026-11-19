package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v16 (reveals across devices, #1542): `matches_local` gains the nullable `ownerUid` and `revealUploaded` (0).
 * Additive only; every kept row, a seen reveal included, starts ownerless and not uploaded, so the next sign-in claims
 * and uploads it.
 */
object RevealSyncMigration {

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `matches_local` ADD COLUMN `ownerUid` TEXT")
            db.execSQL("ALTER TABLE `matches_local` ADD COLUMN `revealUploaded` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
