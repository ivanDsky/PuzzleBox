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
    /** LinkedIn account: imports the player's own results and shows connections as ghost rows (ADR-0028). Live. */
    LinkedInAccount(label = "LinkedIn: account results + connections", releaseDefault = true),

    /** Cloud sync: the signed-in account's history, puzzles and saved boards on every device (ADR-0029). */
    CloudSync(label = "Cloud sync: history on every device", releaseDefault = true),

    /** Ranks hub: per-game and Overall daily boards, the calendar as History, the visibility ladder (ADR-0031). */
    LeaderboardHub(label = "Ranks hub", releaseDefault = false),
}
