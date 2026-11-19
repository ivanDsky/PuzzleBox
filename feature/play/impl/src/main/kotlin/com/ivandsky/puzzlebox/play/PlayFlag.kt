package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** feature/play's release flags (ADR-0022): registered by `flagSet("play", PlayFlag.entries)` in [com.ivandsky.puzzlebox.play.di.playModule]. */
internal enum class PlayFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Archive calendar month change (backlog #1551): a switch slides the new grid in from the side it was paged to; off, it
     * crossfades in place. A debug-only variant switch for the hands-on check: the flicker fix itself is live either way.
     * Read only in [LibraryViewModel] (ADR-0022 §3); the entry and the losing transition go once the check picks one.
     */
    ArchiveMonthSlide(label = "Archive month change slides in (off: crossfade)", releaseDefault = false),

    /**
     * Community's filter as a sheet (backlog #1548): off, Game and Difficulty are the head's ⋮ menu; on, a filter icon on New
     * today's page opens a bottom sheet of chips and the filter in force shows as a chip that clears it. Read only in
     * [com.ivandsky.puzzlebox.play.community.CommunityViewModel] (ADR-0022 §3); the entry and the ⋮ menu go once the check lands.
     */
    CommunityFilterSheet(label = "Community filter opens as a sheet (off: ⋮ menu)", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
