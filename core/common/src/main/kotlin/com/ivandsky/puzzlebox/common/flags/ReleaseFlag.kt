package com.ivandsky.puzzlebox.common.flags

/**
 * The central release flags (ADR-0022), frozen: new flags go in their owner's `<Owner>Flag` (ADR-0022 amendment
 * 2026-11-18), and konsist P15 fails on a new entry here. Each entry keeps one unfinished trunk feature dark; its flip
 * PR sets [releaseDefault] `true` (with the version bump) here in place. Its cleanup ticket, or the slice that moves it
 * to its owner, deletes the entry and its name in P15's `FROZEN` in the same PR.
 *
 * @property label what the flag turns on, shown in the debug Settings' Release flags section.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class ReleaseFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Result hero polish (backlog #1162, #1170, #1171): previous and best as two labelled columns, a signed-out sign-in line
     * under the time, and the day's total on the full-set moment. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    ResultHeroPolish(label = "Result hero polish", releaseDefault = true),

    /**
     * Hint polish (backlog #1172, #1174): the Hint button reads as a peer of Undo, and hint captions explain the step in a plain
     * sentence before any technique name. Read only in feature/play's DI or a game UI module's DI (ADR-0022 §3).
     */
    HintPolish(label = "Calm hint button and plain hint captions", releaseDefault = false),

    /**
     * Zip's next number pulses itself (backlog #1151): the number's own dot breathes in place of the separate ring that
     * blinks round it. Read only in feature/play's DI, which hands the Zip board one Boolean (ADR-0022 §3, spec A2).
     */
    ZipDotPulse(label = "Zip next number pulses itself", releaseDefault = true),

    /**
     * Sheets that opened other sheets become screens (epic nested-sheets): New duel is a screen on the phone and fills the
     * Duels pane on the tablet, and its Add puzzle step is a page of it. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    SheetScreens(label = "New duel as a screen", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
