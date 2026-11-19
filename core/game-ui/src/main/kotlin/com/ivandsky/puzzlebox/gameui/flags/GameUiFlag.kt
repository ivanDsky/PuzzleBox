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
     * the Train tab in Games shows once any adapter is bound. Read by that game UI module's DI (the adapter binding)
     * and by :app's sync binding: while any Train flag is on the `trainer` kind syncs Trainer progress, and it joins or
     * leaves sync at the next app start (ADR-0022 §3).
     */
    TrainQueens(label = "Queens in the Train tab", releaseDefault = false),

    /**
     * Technique trainer for Tango (epic trainer): Tango's UI module binds its trainer adapter only while this is on.
     * Read by that game UI module's DI (the adapter binding) and by :app's sync binding: while any Train flag is on the
     * `trainer` kind syncs Trainer progress, and it joins or leaves sync at the next app start (ADR-0022 §3).
     */
    TrainTango(label = "Tango in the Train tab", releaseDefault = false),

    /**
     * Technique trainer for Mini Sudoku (epic trainer): Mini Sudoku's UI module binds its trainer adapter only while this
     * is on. Read by that game UI module's DI (the adapter binding) and by :app's sync binding: while any Train flag is
     * on the `trainer` kind syncs Trainer progress, and it joins or leaves sync at the next app start (ADR-0022 §3).
     */
    TrainSudoku(label = "Mini Sudoku in the Train tab", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
