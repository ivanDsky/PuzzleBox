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

    /** Flat buttons and cards: no lips, a tint plus a 100 ms scale on press. Read only by the app's root theme (nav-rethink B1). */
    FlatSurfaces(label = "Flat buttons and cards", releaseDefault = false),

    /**
     * Custom inside Games and a full-screen Studio: Create stops being a tab, Games gets a Custom pill, and the Studio
     * hides the bar (nav-rethink N9, N10). Read only in a ViewModel, a DI module or the app-graph `TabSetSource` (ADR-0027 §10).
     */
    CustomInGames(label = "Custom inside Games, full-screen Studio", releaseDefault = false),
}
