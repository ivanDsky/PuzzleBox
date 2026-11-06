package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * WHY: every schema change bumps the [PuzzleBoxDatabase] version, exports a schema JSON
 * under core/database/schemas (committed), and adds a Migration here; builders wire [ALL].
 * Destructive fallbacks are not allowed once Phase 0 ships.
 */
object Migrations {

    /** v2 (ticket 12): adds the daily envelope store `puzzle_dailies`; v1 rows untouched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `puzzle_dailies` (
                    `gameId` TEXT NOT NULL,
                    `day` TEXT NOT NULL,
                    `puzzleNumber` INTEGER,
                    `variant` TEXT NOT NULL,
                    `formatVersion` INTEGER NOT NULL,
                    `payload` BLOB NOT NULL,
                    `fetchedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`gameId`, `day`)
                )
                """.trimIndent(),
            )
        }
    }

    /** v3 (ticket 15): adds the completions store (records + replays, A4/A5); v2 rows untouched. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `completions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `puzzleRef` TEXT NOT NULL,
                    `timeMs` INTEGER NOT NULL,
                    `hints` INTEGER NOT NULL,
                    `statsVersion` INTEGER NOT NULL,
                    `statsBlob` TEXT NOT NULL,
                    `completedAtMs` INTEGER NOT NULL,
                    `firstCompletedAtMs` INTEGER,
                    `isRecord` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_completions_puzzleRef` ON `completions` (`puzzleRef`)",
            )
        }
    }

    /**
     * v4 (rearchitecture): `daily_puzzles` becomes the opaque envelope — the raw fetched
     * bytes. v1–v3 rows stored parsed columns (size, cells-as-JSON via the deleted
     * CellsConverter) and never kept the raw payload, so it cannot be reconstructed;
     * old rows migrate with a zero-byte payload and are refetched on next use.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
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

    /**
     * v5: drops the legacy `daily_puzzles` exemplar table; nothing reads it since the
     * LinkedIn feed shipped.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `daily_puzzles`")
        }
    }

    /** v6 (navigation-ux): adds the saved in-progress plays store `saved_plays` (A10); v5 rows untouched. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_plays` (" +
                    "`gameId` TEXT NOT NULL, " +
                    "`day` TEXT NOT NULL, " +
                    "`blob` BLOB NOT NULL, " +
                    "`elapsedMs` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`gameId`, `day`))",
            )
        }
    }

    /**
     * v7 (editor mode, A6/A13): adds `drafts`, `published_puzzles`, `received_puzzles` and
     * `custom_saved_plays` in one bump; only CREATE TABLE, v6 rows untouched.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drafts` (" +
                    "`id` TEXT NOT NULL, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`variant` TEXT NOT NULL, " +
                    "`draftFormatVersion` INTEGER NOT NULL, " +
                    "`blob` BLOB NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "`testLayoutKey` TEXT, " +
                    "`testTimeMs` INTEGER, " +
                    "`testHints` INTEGER, " +
                    "`reservedCode` TEXT, " +
                    "`imported` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `published_puzzles` (" +
                    "`code` TEXT NOT NULL, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`variant` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`authorTimeMs` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`unpublished` INTEGER NOT NULL, " +
                    "`solvesCount` INTEGER, " +
                    "`solvesCountAt` INTEGER, " +
                    "PRIMARY KEY(`code`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `received_puzzles` (" +
                    "`code` TEXT NOT NULL, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`variant` TEXT NOT NULL, " +
                    "`sharedFormatVersion` INTEGER NOT NULL, " +
                    "`puzzleBytes` BLOB NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`authorName` TEXT NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`authorTimeMs` INTEGER NOT NULL, " +
                    "`authorHints` INTEGER NOT NULL, " +
                    "`authorUid` TEXT NOT NULL, " +
                    "`openedAt` INTEGER NOT NULL, " +
                    "`unpublished` INTEGER NOT NULL, " +
                    "`postState` TEXT NOT NULL, " +
                    "`postAttempts` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`code`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_saved_plays` (" +
                    "`puzzleId` TEXT NOT NULL, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`blob` BLOB NOT NULL, " +
                    "`elapsedMs` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`puzzleId`))",
            )
        }
    }

    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
