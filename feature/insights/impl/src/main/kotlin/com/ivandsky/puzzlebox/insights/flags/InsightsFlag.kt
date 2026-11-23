package com.ivandsky.puzzlebox.insights.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * The insights feature's release flags (ADR-0022 amendment 2026-11-18), registered as the `insights` set in
 * [com.ivandsky.puzzlebox.insights.di.insightsModule]. Each entry keeps one part of the epic dark; its flip PR sets
 * [releaseDefault] `true` (with the version bump) here, and its cleanup ticket deletes the entry. Public because the app
 * reads them where it wires an entry point. The destinations themselves are registered ungated; a flag gates the way in.
 *
 * @property label what the flag turns on, shown in the debug Settings' Release flags section.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class InsightsFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * The stats page's filters (epic stats-page): the filter bar and its sheets on a game's and all games' stats. Ships
     * dark until its flip PR; nothing reads it yet. Read only in a ViewModel, a DI module or the app (ADR-0022 §3).
     */
    StatsFilters(label = "Stats filters", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
