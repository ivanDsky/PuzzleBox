package com.ivandsky.puzzlebox.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

/**
 * The app's one navigation surface: observable [state] over the pure [NavigationState]
 * reducer, one back stack per tab and a layer above them.
 *
 * Feature entries receive a Navigator, never the state's stacks, and each verb applies the
 * reducer verb of the same name. Every rule (reselect pops to the root, back at a non-start
 * root selects the start tab, roots can't be pushed, a tab tap or a push closes the layer)
 * lives in [NavigationState].
 */
@Stable
class Navigator(initial: NavigationState) {

    /** The current tab, stacks and layer; snapshot state, so readers recompose on every move. */
    var state: NavigationState by mutableStateOf(initial)
        private set

    /** Selects [tab] and closes the layer; reselecting the current tab with no layer open pops it to its root. */
    fun selectTab(tab: NavKey) {
        state = state.selectTab(tab)
    }

    /** A link's switch to [target]: its stack becomes its root plus [key], and one Back returns to where the link was tapped. */
    fun switchTo(target: NavKey, key: NavKey? = null) {
        state = state.switchTo(target, key)
    }

    /** Pushes [key] on the current tab's stack, closing the layer first. */
    fun push(key: NavKey) {
        state = state.push(key)
    }

    /** [push], but a double tap on a destination already on top is ignored. */
    fun pushOnce(key: NavKey) {
        state = state.pushOnce(key)
    }

    /**
     * Back: pops the layer while it is open, else the current stack, or at a non-start root
     * selects the start tab. Returns false, leaving [state] unchanged, at the start root,
     * where the app exits.
     */
    fun pop(): Boolean {
        val next = state.pop() ?: return false
        state = next
        return true
    }

    /** Replaces the top key of the layer while it is open, else of the current stack; a no-op at a root. */
    fun replaceTop(key: NavKey) {
        state = state.replaceTop(key)
    }

    /** Opens the layer above the tabs at [key], replacing any pages it held. */
    fun openLayer(key: NavKey) {
        state = state.openLayer(key)
    }

    /** Pushes [key] on top of the layer; a double tap on a page already on top is ignored. */
    fun pushLayer(key: NavKey) {
        state = state.pushLayer(key)
    }
}
