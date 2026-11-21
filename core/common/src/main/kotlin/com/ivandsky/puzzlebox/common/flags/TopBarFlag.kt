package com.ivandsky.puzzlebox.common.flags

/**
 * The main tabs' top bars (backlog #1597). Declared here because three modules read it (feature/play's Games and
 * Community, feature/history's Ranks, and the app's shell for the avatar): the lowest module they share (ADR-0022
 * amendment 2026-11-18, decision 3).
 */
enum class TopBarFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Games, Community and Ranks open on Today's large top bar, with the phone's avatar at its end on all four tabs.
     * Read only in a ViewModel or a DI module (ADR-0022 §3).
     */
    MainTabsLargeBar(label = "Main tabs share Today's top bar", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
