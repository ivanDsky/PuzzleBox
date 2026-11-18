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
    /** Flat buttons and cards: no lips, a tint plus a 100 ms scale on press. Read only by the app's root theme (nav-rethink B1). */
    FlatSurfaces(label = "Flat buttons and cards", releaseDefault = true),

    /**
     * Strands' flat look (no lip under a token, a 64 dp hint row), split from [FlatSurfaces] so that can ship without
     * touching Strands; pending its own prototype. Read only by the app's root theme (spec A2).
     */
    FlatStrands(label = "Flat Strands board", releaseDefault = false),

    /**
     * Result hero polish (backlog #1162, #1170, #1171): previous and best as two labelled columns, a signed-out sign-in line
     * under the time, and the day's total on the full-set moment. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    ResultHeroPolish(label = "Result hero polish", releaseDefault = false),

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
     * Hint polish (backlog #1172, #1174): the Hint button reads as a peer of Undo, and hint captions explain the step in a plain
     * sentence before any technique name. Read only in feature/play's DI or a game UI module's DI (ADR-0022 §3).
     */
    HintPolish(label = "Calm hint button and plain hint captions", releaseDefault = false),

    /**
     * Same feel in every game (spec A19): one Boolean that every play board hands its games. Read only in feature/play's DI,
     * which binds it once and provides it to the games as LocalPlayFeel (spec A2).
     */
    PlayFeel(label = "Same feel in every game", releaseDefault = false),

    /**
     * Duel yourself (Past you): play a puzzle against your own earlier solve. Read only in a ViewModel or a DI module
     * (ADR-0022 §3).
     */
    VirtualDuel(label = "Duel yourself (Past you)", releaseDefault = true),
}
