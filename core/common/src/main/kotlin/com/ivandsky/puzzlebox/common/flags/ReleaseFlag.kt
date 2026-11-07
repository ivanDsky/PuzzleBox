package com.ivandsky.puzzlebox.common.flags

/**
 * Release flags (ADR-0022): each keeps one unfinished trunk feature dark, so its slices merge
 * to main while release builds never reach it. A feature's first ticket adds its entry with
 * [releaseDefault] `false`; its flip PR sets `true` (with the version bump); its cleanup
 * ticket deletes the entry and the gate. No entry means no feature in flight.
 *
 * @property label what the flag turns on, shown in the debug Catalog's Release flags tab.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class ReleaseFlag(val label: String, val releaseDefault: Boolean) {
    /** Publishing custom puzzles, friend puzzles by code or link, and their leaderboards. */
    FriendSharing(label = "Friend sharing", releaseDefault = false),

    /** The Zip puzzle editor. */
    ZipEditor(label = "Zip editor", releaseDefault = false),

    /** The Tango puzzle editor. */
    TangoEditor(label = "Tango editor", releaseDefault = false),

    /** The Mini Sudoku editor, and the debug-only 9×9 one. */
    SudokuEditor(label = "Sudoku editor", releaseDefault = false),

    /** The Strands puzzle editor. */
    StrandsEditor(label = "Strands editor", releaseDefault = false),
}
