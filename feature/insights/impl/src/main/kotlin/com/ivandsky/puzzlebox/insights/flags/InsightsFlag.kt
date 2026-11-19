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
     * The monthly recap (epic insights): a Today card on the first days of a month and a Profile entry that open the
     * month's story. Read only in a ViewModel, a DI module or the app (ADR-0022 §3).
     */
    MonthlyRecap(label = "Monthly recap", releaseDefault = false),

    /**
     * The yearly recap (epic insights): a Profile entry from Dec 20 and a Today card in early January, and past years
     * from Profile. Read only in a ViewModel, a DI module or the app (ADR-0022 §3).
     */
    YearRecap(label = "Yearly recap", releaseDefault = false),

    /**
     * The stats page (epic insights): the game page's stats block and Profile open a game's or all games' trends,
     * records and friends comparison. Read only in a ViewModel, a DI module or the app (ADR-0022 §3).
     */
    StatsPage(label = "Stats page", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
