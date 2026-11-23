package com.ivandsky.puzzlebox.play.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * The play feature's release flags (ADR-0022 amendment 2026-11-18), registered as the `play` set in
 * [com.ivandsky.puzzlebox.play.di.playModule]. Each entry keeps one unfinished trunk feature dark; its flip PR sets
 * [releaseDefault] `true` (with the version bump) here, and its cleanup ticket deletes the entry.
 *
 * @property label what the flag turns on, shown in the debug Settings' Release flags section.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class PlayFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Per-puzzle solve counter (#2032): a player's first entry on a shared puzzle's board also bumps the puzzle's
     * `solves` in the same write. Read by [com.ivandsky.puzzlebox.play.di.playModule]'s
     * [com.ivandsky.puzzlebox.data.repository.SolveCounterSwitch] binding, which the backend's board submit asks at
     * each write. On in release: the production rules that allow the bump and the backfill are live.
     */
    SolveCounter(label = "Per-puzzle solve counter", releaseDefault = true),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
