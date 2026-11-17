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
     * Strands' flat look (no lip under a token, a 64 dp hint row), split from [FlatSurfaces] so that can ship without
     * touching Strands; pending its own prototype. Read only by the app's root theme (spec A2).
     */
    FlatStrands(label = "Flat Strands board", releaseDefault = false),

    /**
     * Custom inside Games and a full-screen Studio: Create stops being a tab, Games gets a Custom pill, and the Studio
     * hides the bar (nav-rethink N9, N10). Read only in a ViewModel, a DI module or the app-graph `TabSetSource` (ADR-0027 §10).
     */
    CustomInGames(label = "Custom inside Games, full-screen Studio", releaseDefault = true),

    /**
     * Result moments, celebration and the next-thing chain (nav-rethink L1–L4): every M4 behaviour on Result. Read only in
     * a ViewModel or a DI module (ADR-0022 §3, ADR-0027 §10).
     */
    ResultMoments(label = "Result moments, celebration and next-thing chain", releaseDefault = true),

    /**
     * The readable board look (nav-rethink B3): shared board tokens and geometry for all five games. Read only by the app's
     * root theme, which hands the theme one Boolean (spec A2).
     */
    BoardTokens(label = "Readable board look", releaseDefault = true),

    /**
     * Hints give a real first move (nav-rethink B4): a hint never points at what the player already knows. Read only in a
     * game UI module's DI, which picks the [HintMode][com.ivandsky.puzzlebox.gameui.hint.HintMode] at registration (spec A2).
     */
    HintFirstMove(label = "Hints give a real first move", releaseDefault = true),

    /**
     * Finish for me in the Studio (nav-rethink E1): a game whose editor has a `DraftCompleter` completes a half-built draft
     * into a unique puzzle, keeping every piece the author placed. Read only in a ViewModel or a DI module (spec A2).
     */
    FinishForMe(label = "Finish for me in the Studio", releaseDefault = true),

    /**
     * Community puzzles in Custom (nav-rethink C1, C2): public publishing, the New today list and reporting a puzzle.
     * Read only in a ViewModel or a DI module (spec A2).
     */
    CommunityPuzzles(label = "Community puzzles in Custom", releaseDefault = true),

    /**
     * Fixed play controls and board size caps (nav-rethink B5, B6). Read only by the play-tuning source binding in the
     * game UI DI, which holds a non-null `PlayTuning` only while this is on (spec A2).
     */
    BoardErgonomics(label = "Fixed play controls and board size caps", releaseDefault = true),

    /**
     * Same feel in every game (spec A19): one Boolean that every play board hands its games. Read only in feature/play's DI,
     * which binds it once and provides it to the games as LocalPlayFeel (spec A2).
     */
    PlayFeel(label = "Same feel in every game", releaseDefault = false),

    /**
     * Duel yourself (Past you): play a puzzle against your own earlier solve. Read only in a ViewModel or a DI module
     * (ADR-0022 §3).
     */
    VirtualDuel(label = "Duel yourself (Past you)", releaseDefault = false),

    /**
     * Tablet crossfades (backlog #1118, #1119): rail tab switches, in-tab page changes and the Games right pane fade in
     * place instead of rising or sliding. Read only by the app-graph's nav shell and feature/play's DI, which each hand
     * their composables one Boolean (ADR-0022).
     */
    TabletCrossfade(label = "Tablet crossfades", releaseDefault = false),
}
