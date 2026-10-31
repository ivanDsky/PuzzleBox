package com.ivandsky.puzzlebox.data.db

import androidx.room.migration.Migration

/**
 * WHY: every schema change bumps the [PuzzleBoxDatabase] version, exports a schema JSON
 * under core/data/schemas (committed), and adds a Migration here; builders wire [ALL].
 * Destructive fallbacks are not allowed once Phase 0 ships.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
