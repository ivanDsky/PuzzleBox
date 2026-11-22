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
     * Community shelves (epic community-browse): the Community tab shows the shelves home instead of its three tabs.
     * Read by the Community route, which gates the tab's whole body (ADR-0022 §5: gate the entry).
     */
    CommunityShelves(label = "Community shelves", releaseDefault = false),

    /**
     * Community create FAB (#2013): a New puzzle button floats over every Community tab and Yours' New puzzle row is
     * gone. Read by the Community route, which hands the FAB and the sheet host to the screen (ADR-0022 §5).
     */
    CreateFab(label = "Community create FAB", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
