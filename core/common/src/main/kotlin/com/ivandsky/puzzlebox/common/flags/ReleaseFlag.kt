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
     * Rules sheet (backlog #1187): How to play on a play board opens a short rules sheet for the game, with replaying the
     * tutorial as a secondary action. Read only in feature/play's DI (ADR-0022 §3).
     */
    RulesSheet(label = "Rules sheet behind How to play", releaseDefault = true),

    /**
     * Hint polish (backlog #1172, #1174): the Hint button reads as a peer of Undo, and hint captions explain the step in a plain
     * sentence before any technique name. Read only in feature/play's DI or a game UI module's DI (ADR-0022 §3).
     */
    HintPolish(label = "Calm hint button and plain hint captions", releaseDefault = false),

    /**
     * Duel lineup (epic #1226): Customize builds a duel puzzle by puzzle, each entry any puzzle of a game or one exact
     * puzzle. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    DuelLineup(label = "Duel lineup built puzzle by puzzle", releaseDefault = true),

    /**
     * Random puzzle (epic #1153): a Random action opens one puzzle picked from the daily archive's past days or from
     * Community's New today, narrowed by game and difficulty. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    RandomPuzzle(label = "Random puzzle from the archive or Community", releaseDefault = true),

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
     * Result share (epic #1188): the daily result's Share text gains a solve strip and a link to that day. Read only in
     * a ViewModel or a DI module (ADR-0022 §3).
     */
    ShareResult(label = "Result share with link and solve strip", releaseDefault = true),

    /**
     * Zip's next number pulses itself (backlog #1151): the number's own dot breathes in place of the separate ring that
     * blinks round it. Read only in feature/play's DI, which hands the Zip board one Boolean (ADR-0022 §3, spec A2).
     */
    ZipDotPulse(label = "Zip next number pulses itself", releaseDefault = true),

    /**
     * What's new screen (epic #1363): Settings' About gets a What's new row that opens the page of what each release brought.
     * Read only in feature/profile's DI, which hands Settings one Boolean (ADR-0022 §3).
     */
    WhatsNew(label = "What's new screen", releaseDefault = true),
}
