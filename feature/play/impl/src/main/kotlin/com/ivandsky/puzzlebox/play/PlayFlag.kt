package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * feature/play's release flags (ADR-0022 amendment 2026-11-18): registered by `flagSet("play", PlayFlag.entries)` in
 * [com.ivandsky.puzzlebox.play.di.playModule]. Public because the app's graph tests turn its entries on.
 */
enum class PlayFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Archive calendar months swipe (backlog #1668): the grid follows the finger and snaps to the previous or next month,
     * and ‹ ›, the jumps and "Today" slide the grid the same way. Off, a month change is #1551's crossfade.
     * Read only in [LibraryViewModel] (ADR-0022 §3).
     */
    ArchiveMonthSwipe(label = "Archive calendar swipes between months (off: crossfade)", releaseDefault = false),

    /**
     * Result hero polish (backlog #1162, #1170, #1171): previous and best as two labelled columns, a signed-out sign-in line
     * under the time, and the day's total on the full-set moment. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    ResultHeroPolish(label = "Result hero polish", releaseDefault = true),

    /**
     * Zip's next number pulses itself (backlog #1151): the number's own dot breathes in place of the separate ring that
     * blinks round it. Read only in feature/play's DI, which hands the Zip board one Boolean (ADR-0022 §3, spec A2).
     */
    ZipDotPulse(label = "Zip next number pulses itself", releaseDefault = true),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
