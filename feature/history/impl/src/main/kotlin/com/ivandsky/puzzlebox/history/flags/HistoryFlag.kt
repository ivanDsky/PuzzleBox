package com.ivandsky.puzzlebox.history.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * The history feature's release flags (ADR-0022 amendment 2026-11-18), registered as the `history` set in
 * [com.ivandsky.puzzlebox.history.di.historyModule]. Each entry keeps one unfinished trunk feature dark; its flip PR sets
 * [releaseDefault] `true` (with the version bump) here, and its cleanup ticket deletes the entry.
 *
 * @property label what the flag turns on, shown in the debug Settings' Release flags section.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class HistoryFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * A game's Week and Month boards rank by the sum of its daily place points, as Overall does across games, not by the
     * average of the days played (backlog #1698). Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    WeekMonthPoints(label = "Week and Month boards add up daily points", releaseDefault = false),

    /**
     * The Ranks hub's Friends boards (Day, Week, Month, Overall and the viewer's place) also count a friend who placed
     * past the global page, read for each board whose top 100 is full (backlog #1767). Off, such a friend is missing,
     * as before. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    FriendsPastGlobalPage(label = "Friends boards count friends past the top 100", releaseDefault = true),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
