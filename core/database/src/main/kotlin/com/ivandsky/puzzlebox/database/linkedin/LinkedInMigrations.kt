package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * WHY: every schema change bumps the [LinkedInDatabase] version, exports a schema JSON under
 * core/database/schemas (committed), and adds a Migration here; the builder wires [ALL].
 * Destructive fallbacks are not allowed.
 */
object LinkedInMigrations {

    /**
     * v2 (cloud sync, ADR-0029 §7): additive only, every ghost and fetch-log row is kept. The new table
     * `saved_boards` is backfilled with one board per (game, day) that has ghosts and a fetch-log profile:
     * the profile of that day's board read (else its newest entry), `readAtMs` the newest ghost read and
     * `complete` 0, since the day's end at read time is unknown. A day with no fetch-log entry gets no board.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_boards` (" +
                    "`game` TEXT NOT NULL, " +
                    "`day` TEXT NOT NULL, " +
                    "`profileId` TEXT NOT NULL, " +
                    "`ownerUid` TEXT, " +
                    "`readAtMs` INTEGER NOT NULL, " +
                    "`complete` INTEGER NOT NULL, " +
                    "`uploadedFingerprint` TEXT, " +
                    "PRIMARY KEY(`game`, `day`))",
            )
            db.execSQL(
                "INSERT INTO `saved_boards` (`game`, `day`, `profileId`, `ownerUid`, `readAtMs`, `complete`, `uploadedFingerprint`) " +
                    "SELECT g.`game`, g.`day`, " +
                    "(SELECT f.`profileId` FROM `fetch_log` f WHERE f.`game` = g.`game` AND f.`day` = g.`day` " +
                    "ORDER BY (f.`item` = 'Board') DESC, f.`fetchedAtMs` DESC, f.`profileId` LIMIT 1), " +
                    "NULL, MAX(g.`fetchedAtMs`), 0, NULL " +
                    "FROM `ghosts` g GROUP BY g.`game`, g.`day` " +
                    "HAVING EXISTS (SELECT 1 FROM `fetch_log` f WHERE f.`game` = g.`game` AND f.`day` = g.`day`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
