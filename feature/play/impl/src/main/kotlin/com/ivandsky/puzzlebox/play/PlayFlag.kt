package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** feature/play's release flags (ADR-0022): registered by `flagSet("play", PlayFlag.entries)` in [com.ivandsky.puzzlebox.play.di.playModule]. */
internal enum class PlayFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Archive calendar months swipe (backlog #1668): the grid follows the finger and snaps to the previous or next month,
     * and ‹ ›, the jumps and "Today" slide the grid the same way. Off, a month change is #1551's crossfade.
     * Read only in [LibraryViewModel] (ADR-0022 §3).
     */
    ArchiveMonthSwipe(label = "Archive calendar swipes between months (off: crossfade)", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
