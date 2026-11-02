package com.ivandsky.puzzlebox.model

/**
 * App-wide theme choice (ticket 05). Lives in :core:model so the preferences contract
 * (:core:data) and the app/home UI share one definition — the same convention as the
 * shared domain types above.
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
