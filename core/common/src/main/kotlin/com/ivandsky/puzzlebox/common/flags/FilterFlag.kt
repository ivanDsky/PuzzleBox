package com.ivandsky.puzzlebox.common.flags

/**
 * The filter chips' flags (epic picker-filters). Declared here because four modules read it (the Add puzzle picker,
 * Community, Random puzzle and Ranks): the lowest module they share (ADR-0022 amendment 2026-11-18, decision 3).
 */
enum class FilterFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Every filter is a dropdown chip that opens a bottom sheet of options: the Add puzzle picker, Community, Random puzzle
     * and Ranks. Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    FilterSheets(label = "Filters as chips and sheets", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
