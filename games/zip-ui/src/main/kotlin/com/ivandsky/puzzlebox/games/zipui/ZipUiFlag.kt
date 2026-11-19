package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** games/zip-ui's release flags (ADR-0022): registered by `flagSet("zip-ui", ZipUiFlag.entries)` in [com.ivandsky.puzzlebox.games.zipui.di.zipUiFlagModule]. */
internal enum class ZipUiFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Zip editor modes (backlog #1543): Numbers and Walls become one Numbers & walls mode, a tap in a cell numbering it and
     * a tap on its edge drawing a wall; off, the editor keeps its Path, Numbers and Walls modes. Read only in
     * [zipUiModule] (ADR-0022 §3), when the editor opens; the entry goes once the one-mode editor is the only one.
     */
    NumbersAndWalls(label = "Zip editor: Numbers and Walls as one mode", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
