package com.ivandsky.puzzlebox.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * WHY: every schema change bumps the [PuzzleBoxDatabase] version, exports a schema JSON
 * under core/data/schemas (committed), and adds a Migration here; builders wire [ALL].
 * Destructive fallbacks are not allowed once Phase 0 ships.
 */
object Migrations {
    /**
     * v1 stored parsed columns (size, cells-as-JSON via the deleted CellsConverter);
     * v2 stores the opaque envelope: the raw fetched bytes. Old rows never kept the raw
     * payload and it cannot be reconstructed, so they migrate with a zero-byte payload.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_puzzles_new` (" +
                    "`gameId` TEXT NOT NULL, " +
                    "`day` TEXT NOT NULL, " +
                    "`payload` BLOB NOT NULL, " +
                    "`fetchedAtMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`gameId`, `day`))",
            )
            db.execSQL(
                "INSERT INTO `daily_puzzles_new` (`gameId`, `day`, `payload`, `fetchedAtMs`) " +
                    "SELECT `gameId`, `day`, X'', `fetchedAtMs` FROM `daily_puzzles`",
            )
            db.execSQL("DROP TABLE `daily_puzzles`")
            db.execSQL("ALTER TABLE `daily_puzzles_new` RENAME TO `daily_puzzles`")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
