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
    /** Post a PuzzleBox solve of today's LinkedIn daily to the player's LinkedIn account (ADR-0032). Experimental. */
    LinkedInPush(label = "Post results to LinkedIn", releaseDefault = false),

    /** Nav rethink M1: Today's summaries open Ranks, Ranks gets its Tune and hero doors, and Profile becomes a layer. */
    NavDoors(label = "Tappable summaries, Ranks doors and Profile layer", releaseDefault = false),
}
