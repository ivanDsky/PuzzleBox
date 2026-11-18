package com.ivandsky.puzzlebox.common.flags

/**
 * Release flags (ADR-0022): each keeps one unfinished trunk feature dark, so its slices merge
 * to main while release builds never reach it. A feature's first ticket adds its entry with
 * [releaseDefault] `false`; its flip PR sets `true` (with the version bump); its cleanup
 * ticket deletes the entry and the gate. No entry means no feature in flight.
 *
 * @property label what the flag turns on, shown in the debug Catalog's Release flags tab.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class ReleaseFlag(val label: String, val releaseDefault: Boolean) {
    /** Post a PuzzleBox solve of today's LinkedIn daily to the player's LinkedIn account (ADR-0032). Experimental. */
    LinkedInPush(label = "Post results to LinkedIn", releaseDefault = true),

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
     * Same feel in every game (spec A19): one Boolean that every play board hands its games. Read only in feature/play's DI,
     * which binds it once and provides it to the games as LocalPlayFeel (spec A2).
     */
    PlayFeel(label = "Same feel in every game", releaseDefault = true),

    /**
     * Ranks shows when LinkedIn was last read (backlog #1357): a muted line under a game's Friends day board with when its
     * LinkedIn part was last read, and a pull on the board that also asks LinkedIn for that day, inside the read windows.
     * Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    RanksFreshness(label = "Ranks shows when LinkedIn was last read", releaseDefault = false),

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
}
