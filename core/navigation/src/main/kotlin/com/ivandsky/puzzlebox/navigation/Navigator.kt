package com.ivandsky.puzzlebox.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The app's one navigation surface over its single [NavBackStack] (NiA's per-tab stacks are
 * deliberately not adopted: tab back behaviour is product, and it stays one stack).
 *
 * Feature entries receive a Navigator, never the raw stack, for the shared moves; a feature
 * with its own stack contract (play's result unwind) builds it as an extension over
 * [backStack] in its impl module.
 */
class Navigator(val backStack: NavBackStack<NavKey>) {

    /** The destination on top of the stack — the one on screen. */
    val currentKey: NavKey?
        get() = backStack.lastOrNull()

    /** Push [key] unconditionally (deep pages, replays). */
    fun navigate(key: NavKey) {
        backStack.add(key)
    }

    /**
     * Double-tap guard for content affordances: pushing a destination that is already the
     * stack top would need two backs to unwind — add only when it isn't (Rev17).
     */
    fun navigateOnce(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    /** Tab selection resets to that tab's root — no cross-tab back stacking. */
    fun navigateToTopLevel(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    /** Pop the top entry; a no-op on an empty stack. */
    fun goBack() {
        backStack.removeLastOrNull()
    }
}
