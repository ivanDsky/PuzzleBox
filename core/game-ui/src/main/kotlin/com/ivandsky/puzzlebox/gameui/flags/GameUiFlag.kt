package com.ivandsky.puzzlebox.gameui.flags

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/**
 * The play kit's release flags (ADR-0022 amendment 2026-11-18). Declared here because feature/play and the game UI
 * modules both read them and this is the lowest module they share (decision 3); registered as the `game-ui` set by
 * :app, since core:game-ui has no Koin. Each entry keeps one unfinished trunk feature dark; its flip PR sets
 * [releaseDefault] `true` (with the version bump) here, and its cleanup ticket deletes the entry.
 *
 * @property label what the flag turns on, shown in the debug Settings' Release flags section.
 * @property releaseDefault the value every release build uses; debug builds start from it too.
 */
enum class GameUiFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Technique trainer for Queens (epic trainer): Queens' UI module binds its trainer adapter only while this is on, and
     * the Train tab in Games shows once any adapter is bound. Read only in a game UI module's DI (ADR-0022 §3).
     */
    TrainQueens(label = "Queens in the Train tab", releaseDefault = false),

    /**
     * Technique trainer for Tango (epic trainer): Tango's UI module binds its trainer adapter only while this is on.
     * Read only in a game UI module's DI (ADR-0022 §3).
     */
    TrainTango(label = "Tango in the Train tab", releaseDefault = false),

    /**
     * Technique trainer for Mini Sudoku (epic trainer): Mini Sudoku's UI module binds its trainer adapter only while this
     * is on. Read only in a game UI module's DI (ADR-0022 §3).
     */
    TrainSudoku(label = "Mini Sudoku in the Train tab", releaseDefault = false),

    /**
     * Mode tiles (epic segmented-replace): the work surfaces' mode switches, the Zip editor's step list and the Queens editor's
     * Queen | Paint and Solution A | B, show as quiet violet tiles instead of the segmented control. Lives here because
     * games/zip-ui, games/queens-ui and feature/create/impl all depend on core:game-ui. Read only in a game UI module's DI
     * or a ViewModel (ADR-0022 §3); the entry goes once the tiles are the only control.
     */
    ModeTiles(label = "Mode tiles on the work surfaces: Zip editor, Queens editor, Solution A | B", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
