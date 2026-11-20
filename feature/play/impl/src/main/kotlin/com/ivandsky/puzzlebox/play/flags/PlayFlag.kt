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
     * Result hero's first solve of a puzzle that is not a new best (backlog #1762): leads with "2nd fastest of your 5 Zip"
     * (none when there is no rank to name) instead of "Your best is … · … off". Off, the gap line stays. Read only in
     * [com.ivandsky.puzzlebox.play.di.playModule] (ADR-0022 §3), threaded into the result view model as a Boolean.
     */
    FirstSolveNoGap(label = "Result: first solve not best shows its rank, not the gap to your best", releaseDefault = false),

    /**
     * Game page's Make one as a link (backlog #1567): no full-width button; How to play ends with "Make your own <Game>"
     * beside "Open the lesson". Read only in [com.ivandsky.puzzlebox.play.LibraryViewModel] (ADR-0022 §3).
     */
    MakeOwnLink(label = "Game page: Make your own link in How to play (off: full-width Make one)", releaseDefault = false),

    /**
     * Launcher shortcuts (epic #1578): the long-press menu offers Play Up next and one entry per game. Read only in the
     * app's shortcut DI (ADR-0022 §3).
     */
    AppShortcuts(label = "Launcher shortcuts: Play Up next and one per game", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
