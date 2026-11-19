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
     * Ranks shows when LinkedIn was last read (backlog #1357): a muted line under a game's Friends day board with when its
     * LinkedIn part was last read, and a pull on the board that also asks LinkedIn for that day, inside the read windows.
     * Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    RanksFreshness(label = "Ranks shows when LinkedIn was last read", releaseDefault = true),

    /**
     * The Ranks filter sheet holds only the board's own choices (backlog #1547): Who and When, without the Defaults block
     * ("Opens on", "Who can see you"), which stay in Settings. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    RanksTuneBoardOnly(label = "Ranks filter sheet without Defaults", releaseDefault = false),

    /**
     * A game's Week and Month boards rank by the sum of its daily place points, as Overall does across games, not by the
     * average of the days played (backlog #1698). Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    WeekMonthPoints(label = "Week and Month boards add up daily points", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
