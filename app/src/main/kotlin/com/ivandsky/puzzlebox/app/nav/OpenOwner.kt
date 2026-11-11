package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Goes to [key]'s owner (ADR-0027), through the reducer's own verbs.
 *
 * - A key whose home is `TabRoot(tab)` selects that tab and resets it to its root; nothing
 *   is pushed, and back from the root returns to the start tab. The owner key is found by
 *   [TopLevelDestination.tab] and then looked up in the live `state.tabs`, because the
 *   placement table names tabs, not the stacks that exist.
 * - Every other key (`PushedOn`, `Shell`, `DebugOnly`) is a plain push on the current tab.
 */
internal fun Navigator.openOwner(key: NavKey) {
    val tab = (placementOf(key).home as? Home.TabRoot)?.tab
    if (tab == null) {
        push(key)
        return
    }
    val owner = topLevelDestinations.first { it.tab == tab }.key
    selectTab(owner)
    // Selecting another tab keeps its stack; reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(owner)
}
