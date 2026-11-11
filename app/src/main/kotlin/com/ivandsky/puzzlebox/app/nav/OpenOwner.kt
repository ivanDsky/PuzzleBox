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
 * - Every other key (`PushedOn`, `Shell`, `DebugOnly`), and a `TabRoot` whose tab is not
 *   live, is a plain push on the current tab.
 */
internal fun Navigator.openOwner(key: NavKey) {
    val home = placementOf(key).home
    val owner = (home as? Home.TabRoot)?.let { ownerKey(it.tab) }
    if (owner == null || owner !in state.tabs) {
        push(key)
        return
    }
    selectTab(owner)
    // Selecting another tab keeps its stack; reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(owner)
}

private fun ownerKey(tab: Tab): NavKey? = topLevelDestinations.firstOrNull { it.tab == tab }?.key
