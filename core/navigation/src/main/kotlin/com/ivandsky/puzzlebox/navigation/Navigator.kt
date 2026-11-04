package com.ivandsky.puzzlebox.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

/**
 * The app's one navigation surface: observable [state] over the pure [NavigationState]
 * reducer, one back stack per tab.
 *
 * Feature entries receive a Navigator, never the state's stacks, and each verb applies the
 * reducer verb of the same name. Every rule (reselect pops to the root, back at a non-start
 * root selects the start tab, roots can't be pushed) lives in [NavigationState].
 */
@Stable
class Navigator(initial: NavigationState) {

    /** The current tab and stacks; snapshot state, so readers recompose on every move. */
    var state: NavigationState by mutableStateOf(initial)
        private set

    /** Selects [tab]; reselecting the current tab pops it to its root. */
    fun selectTab(tab: NavKey) {
        state = state.selectTab(tab)
    }

    /** Pushes [key] on the current tab's stack. */
    fun push(key: NavKey) {
        state = state.push(key)
    }

    /** [push], but a double tap on a destination already on top is ignored. */
    fun pushOnce(key: NavKey) {
        state = state.pushOnce(key)
    }

    /**
     * Back: pops the current stack, or at a non-start root selects the start tab. Returns
     * false, leaving [state] unchanged, at the start root, where the app exits.
     */
    fun pop(): Boolean {
        val next = state.pop() ?: return false
        state = next
        return true
    }

    /** Replaces the current stack's top key; a no-op at a root. */
    fun replaceTop(key: NavKey) {
        state = state.replaceTop(key)
    }
}
