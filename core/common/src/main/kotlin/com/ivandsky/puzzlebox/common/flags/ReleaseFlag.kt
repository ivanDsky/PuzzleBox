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
    /** The Zip puzzle editor. */
    ZipEditor(label = "Zip editor", releaseDefault = true),

    /** The Tango puzzle editor. */
    TangoEditor(label = "Tango editor", releaseDefault = false),

    /** The Mini Sudoku editor, and the debug-only 9×9 one. */
    SudokuEditor(label = "Sudoku editor", releaseDefault = false),

    /** The Strands puzzle editor. */
    StrandsEditor(label = "Strands editor", releaseDefault = false),

    /** Import from text: the Create header pill, empty-state button, New-puzzle row and Studio overflow item (spec A23). */
    TextImport(label = "Import from text", releaseDefault = false),

    /** PuzzleBox as an Android share target for plain text (spec A3, A28). */
    ShareTarget(label = "Share text to PuzzleBox", releaseDefault = false),

    /** Hint parity: one deduction per Hint press in Queens, Tango and Mini Sudoku (ADR-0022, spec A20). Live since 0.20.0. */
    HintParity(label = "Hint parity: one deduction per press", releaseDefault = true),

    /** Competitions: async 1v1 duels between friends (spec A16). */
    Competitions(label = "Competitions: 1v1 duels", releaseDefault = false),

    /** Navigation v2: the Today · Games · History · Create tab set, Profile and Settings off the shell (ADR-0027). */
    NavV2(label = "Navigation v2: Today · Games · History · Create", releaseDefault = false),
}
