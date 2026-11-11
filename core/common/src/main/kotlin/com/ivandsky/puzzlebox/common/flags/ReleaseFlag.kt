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
    /** Import from text: the Create header pill, empty-state button, New-puzzle row and Studio overflow item. */
    TextImport(label = "Import from text", releaseDefault = false),

    /** PuzzleBox as an Android share target for plain text. */
    ShareTarget(label = "Share text to PuzzleBox", releaseDefault = false),

    /** Competitions: async 1v1 duels between friends; live since 0.26.0. */
    Competitions(label = "Competitions: 1v1 duels", releaseDefault = true),

    /** LinkedIn account: imports the player's own results and shows connections as ghost rows (ADR-0028). */
    LinkedInAccount(label = "LinkedIn: account results + connections", releaseDefault = false),

    /** Solve replay: every solve keeps its move log so the player can watch it again. */
    FullReplay(label = "Full replay", releaseDefault = false),
}
