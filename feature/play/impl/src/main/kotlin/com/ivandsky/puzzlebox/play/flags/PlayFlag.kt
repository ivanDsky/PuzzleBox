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
     * Game page's Make one as a link (backlog #1567): no full-width button; How to play ends with "Make your own <Game>"
     * beside "Open the lesson". Read only in [com.ivandsky.puzzlebox.play.LibraryViewModel] (ADR-0022 §3).
     */
    MakeOwnLink(label = "Game page: Make your own link in How to play (off: full-width Make one)", releaseDefault = false),

    /**
     * Home-screen widget (epic #1577): today's rings, the streak and Up next on the launcher. Read only in the app's
     * widget DI (ADR-0022 §3).
     */
    HomeWidget(label = "Home-screen widget: today's rings, streak and Up next", releaseDefault = true),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
