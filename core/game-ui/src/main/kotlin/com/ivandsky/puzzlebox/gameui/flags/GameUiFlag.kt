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
     * Hint polish (backlog #1172, #1174): the Hint button reads as a peer of Undo, and hint captions explain the step in a plain
     * sentence before any technique name. Read only in feature/play's DI or a game UI module's DI (ADR-0022 §3).
     */
    HintPolish(label = "Calm hint button and plain hint captions", releaseDefault = true),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
