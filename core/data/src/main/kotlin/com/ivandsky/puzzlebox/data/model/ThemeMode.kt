package com.ivandsky.puzzlebox.data.model

/**
 * App-wide theme choice (ticket 05). Stays next to [DailyPuzzle] until :core:model
 * lands, then moves there — the same convention as the exemplar domain type.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /** Null-safe read of a stored preference string; unknown values fall back to null. */
        fun fromStored(stored: String): ThemeMode? = entries.firstOrNull { it.name == stored }
    }
}
