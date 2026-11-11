package com.ivandsky.puzzlebox.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * WHY: every schema change bumps the [PuzzleBoxDatabase] version, exports a schema JSON
 * under core/database/schemas (committed), and adds a Migration here; builders wire [ALL].
 * Destructive fallbacks are not allowed once Phase 0 ships.
 */
object Migrations {

    /** v2: adds the daily envelope store `puzzle_dailies`; v1 rows untouched. */
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

    /** v3: adds the completions store (records + replays); v2 rows untouched. */
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

    /** v6: adds the saved in-progress plays store `saved_plays`; v5 rows untouched. */
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
     * v7 (editor mode): adds `drafts`, `published_puzzles`, `received_puzzles` and
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
                    "`authorName` TEXT NOT NULL DEFAULT '', " +
                    "`note` TEXT NOT NULL DEFAULT '', " +
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
                    "`authorHints` INTEGER NOT NULL, " +
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
                    "`difficulty` TEXT, " +
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

    /**
     * v8 (competitions): adds `leg_saved_plays`, `matches_local` and `match_results` and the
     * nullable `completions.matchCode`; additive only, v7 rows untouched.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `leg_saved_plays` (" +
                    "`matchCode` TEXT NOT NULL, " +
                    "`legIndex` INTEGER NOT NULL, " +
                    "`gameId` TEXT NOT NULL, " +
                    "`blob` BLOB NOT NULL, " +
                    "`elapsedMs` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`matchCode`, `legIndex`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `matches_local` (" +
                    "`code` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`opponentName` TEXT, " +
                    "`openedAt` INTEGER NOT NULL, " +
                    "`revealSeenAt` INTEGER, " +
                    "`dismissed` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`code`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `match_results` (" +
                    "`matchCode` TEXT NOT NULL, " +
                    "`legIndex` INTEGER NOT NULL, " +
                    "`uid` TEXT NOT NULL, " +
                    "`timeMs` INTEGER NOT NULL, " +
                    "`hints` INTEGER NOT NULL, " +
                    "`completedAtMs` INTEGER NOT NULL, " +
                    "`gaveUp` INTEGER NOT NULL, " +
                    "`postState` TEXT NOT NULL, " +
                    "`postAttempts` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`matchCode`, `legIndex`, `uid`))",
            )
            db.execSQL("ALTER TABLE `completions` ADD COLUMN `matchCode` TEXT")
        }
    }

    /**
     * v9 (LinkedIn account): adds `completions.source` ('app' for a completion earned in the
     * app, 'linkedin' for an import); additive only, every v8 row takes the default 'app'.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `completions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'app'")
        }
    }

    /**
     * v10 (finished duels keep their score): adds `matches_local.revealMyScore` and `revealTheirScore`, both
     * nullable; additive only, a reveal seen under v9 keeps its stamp with no scores.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `matches_local` ADD COLUMN `revealMyScore` INTEGER")
            db.execSQL("ALTER TABLE `matches_local` ADD COLUMN `revealTheirScore` INTEGER")
        }
    }

    /**
     * v12 (full replay): adds the side table `completion_replays`, one row per completion that keeps a
     * solve replay, and the nullable `match_results.replay`; additive only, every v11 row is kept as it is.
     * v11 is [CloudSyncMigration].
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `completion_replays` (" +
                    "`completionId` INTEGER NOT NULL, " +
                    "`replay` BLOB NOT NULL, " +
                    "PRIMARY KEY(`completionId`), " +
                    "FOREIGN KEY(`completionId`) REFERENCES `completions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("ALTER TABLE `match_results` ADD COLUMN `replay` BLOB")
        }
    }

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            CloudSyncMigration.MIGRATION_10_11,
            MIGRATION_11_12,
        )
}
