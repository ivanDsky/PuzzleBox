package com.ivandsky.puzzlebox.play.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * feature/play's release flags (ADR-0022 amendment 2026-11-18): registered by `flagSet("play", PlayFlag.entries)` in
 * [com.ivandsky.puzzlebox.play.di.playModule]. Public because the app's graph tests turn its entries on.
 */
enum class PlayFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Archive calendar months swipe (backlog #1668): the grid follows the finger and snaps to the previous or next month,
     * and ‹ ›, the jumps and "Today" slide the grid the same way. Off, a month change is #1551's crossfade.
     * Read only in [com.ivandsky.puzzlebox.play.LibraryViewModel] (ADR-0022 §3).
     */
    ArchiveMonthSwipe(label = "Archive calendar swipes between months (off: crossfade)", releaseDefault = false),

    /**
     * Result hero polish (backlog #1162, #1170, #1171): previous and best as two labelled columns, a signed-out sign-in line
     * under the time, and the day's total on the full-set moment. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    ResultHeroPolish(label = "Result hero polish", releaseDefault = true),

    /**
     * Game page's Make one as a link (backlog #1567): no full-width button; How to play ends with "Make your own <Game>"
     * beside "Open the lesson". Read only in [com.ivandsky.puzzlebox.play.LibraryViewModel] (ADR-0022 §3).
     */
    MakeOwnLink(label = "Game page: Make your own link in How to play (off: full-width Make one)", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
