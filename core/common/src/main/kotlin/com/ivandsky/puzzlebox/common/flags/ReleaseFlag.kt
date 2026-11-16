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
    LinkedInPush(label = "Post results to LinkedIn", releaseDefault = false),

    /** Flat buttons and cards: no lips, a tint plus a 100 ms scale on press. Read only by the app's root theme (nav-rethink B1). */
    FlatSurfaces(label = "Flat buttons and cards", releaseDefault = true),

    /**
     * Custom inside Games and a full-screen Studio: Create stops being a tab, Games gets a Custom pill, and the Studio
     * hides the bar (nav-rethink N9, N10). Read only in a ViewModel, a DI module or the app-graph `TabSetSource` (ADR-0027 §10).
     */
    CustomInGames(label = "Custom inside Games, full-screen Studio", releaseDefault = false),

    /**
     * Result moments, celebration and the next-thing chain (nav-rethink L1–L4): every M4 behaviour on Result. Read only in
     * a ViewModel or a DI module (ADR-0022 §3, ADR-0027 §10).
     */
    ResultMoments(label = "Result moments, celebration and next-thing chain", releaseDefault = false),

    /**
     * The readable board look (nav-rethink B3): shared board tokens and geometry for all five games. Read only by the app's
     * root theme, which hands the theme one Boolean (spec A2).
     */
    BoardTokens(label = "Readable board look", releaseDefault = false),

    /**
     * Hints give a real first move (nav-rethink B4): a hint never points at what the player already knows. Read only in a
     * game UI module's DI, which picks the [HintMode][com.ivandsky.puzzlebox.gameui.hint.HintMode] at registration (spec A2).
     */
    HintFirstMove(label = "Hints give a real first move", releaseDefault = false),

    /**
     * Finish for me in the Studio (nav-rethink E1): a game whose editor has a `DraftCompleter` completes a half-built draft
     * into a unique puzzle, keeping every piece the author placed. Read only in a ViewModel or a DI module (spec A2).
     */
    FinishForMe(label = "Finish for me in the Studio", releaseDefault = false),

    /**
     * Community puzzles in Custom (nav-rethink C1, C2): public publishing, the New today list and reporting a puzzle.
     * Read only in a ViewModel or a DI module (spec A2).
     */
    CommunityPuzzles(label = "Community puzzles in Custom", releaseDefault = false),
}
