package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Goes to [key]'s owner (ADR-0027), through the reducer's own verbs.
 *
 * - A key whose home is `TabRoot(tab)` switches to that tab at its root ([Navigator.switchTo]); nothing
 *   is pushed, and one back returns to the screen the link was tapped on. The owner key is found by
 *   [TopLevelDestination.tab], because the placement table names tabs, not the stacks that exist.
 * - Every other key (`PushedOn`, `Shell`, `DebugOnly`, `Retired`) is a plain push on the current tab.
 *
 * A door out of the Profile layer closes the layer on the way: the verb does that in the
 * reducer, so the owner's screen is never left under it.
 */
internal fun Navigator.openOwner(key: NavKey) {
    val tab = (placementOf(key).home as? Home.TabRoot)?.tab
    if (tab == null) {
        push(key)
        return
    }
    switchTo(ownerKey(tab))
}

/**
 * Opens [key] on the tab it is pushed on (`PushedOn(tab)`): switches there, the tab's stack becoming its root plus
 * [key], and one back returns to the screen the link was tapped on. A key not pushed on a tab is a plain push.
 */
internal fun Navigator.openOnOwnerTab(key: NavKey) {
    val tab = (placementOf(key).home as? Home.PushedOn)?.tab
    if (tab == null) {
        pushOnce(key)
        return
    }
    val owner = ownerKey(tab)
    // A double tap lands on the same screen: the switch already happened.
    if (state.tab == owner && state.currentStack.last() == key) return
    switchTo(owner, key)
}

private fun ownerKey(tab: Tab): NavKey = topLevelDestinations.first { it.tab == tab }.key
