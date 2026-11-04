package com.ivandsky.puzzlebox.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The selected [tab] plus one back stack per tab; each stack's first key is its tab root.
 * The first stack's root is the [startTab] the app exits through.
 *
 * A pure value: every verb returns a new state and never mutates this one. Invariants are
 * checked on construction and on decode, so a corrupt restored state fails fast:
 * - I1: there are stacks and none is empty;
 * - I2: roots are distinct;
 * - I3: [tab] is one of [tabs];
 * - I4: a root appears only at index 0 of its own stack.
 *
 * Keys serialize polymorphically over [NavKey]; decoding needs the app's NavKey module.
 */
@Immutable
@Serializable
data class NavigationState(val tab: NavKey, val stacks: List<List<NavKey>>) {

    init {
        require(stacks.isNotEmpty() && stacks.none { it.isEmpty() }) { "I1: empty stacks in $stacks" }
        require(stacks.map { it.first() }.toSet().size == stacks.size) { "I2: duplicate roots in $stacks" }
        require(stacks.any { it.first() == tab }) { "I3: $tab is not a tab of $stacks" }
        val roots = stacks.map { it.first() }.toSet()
        require(stacks.all { stack -> stack.drop(1).none { it in roots } }) {
            "I4: a tab root sits above index 0 in $stacks"
        }
    }

    /** Tab roots in stack order. */
    val tabs: List<NavKey> get() = stacks.map { it.first() }

    /** The tab back exits through. */
    val startTab: NavKey get() = stacks.first().first()

    /** The stack rooted at [tab]; throws if [tab] is not a tab. */
    fun stackOf(tab: NavKey): List<NavKey> =
        requireNotNull(stacks.firstOrNull { it.first() == tab }) { "$tab is not a tab of $this" }

    val currentStack: List<NavKey> get() = stackOf(tab)

    /** Tabs whose stacks render, in order: `[start]` or `[start, tab]` ("exit through start"). */
    val visibleTabs: List<NavKey> get() = if (tab == startTab) listOf(startTab) else listOf(startTab, tab)

    /** Keys rendered, bottom to top; the last one is what back removes. */
    val visibleKeys: List<NavKey> get() = visibleTabs.flatMap(::stackOf)

    val allKeys: Set<NavKey> get() = stacks.flatten().toSet()

    /** Selects [tab]; reselecting the current tab pops it to its root. */
    fun selectTab(tab: NavKey): NavigationState {
        stackOf(tab)
        return if (tab == this.tab) withCurrentStack(listOf(tab)) else copy(tab = tab)
    }

    /** Pushes [key] on the current stack; a tab root cannot be pushed. */
    fun push(key: NavKey): NavigationState {
        require(key !in tabs) { "cannot push tab root $key" }
        return withCurrentStack(currentStack + key)
    }

    /** [push], but a no-op when [key] is already on top. */
    fun pushOnce(key: NavKey): NavigationState = if (currentStack.last() == key) this else push(key)

    /**
     * Removes the top key. At a non-start root it selects the start tab and keeps both
     * stacks; at the start root it returns null (the app exits).
     */
    fun pop(): NavigationState? = when {
        currentStack.size > 1 -> withCurrentStack(currentStack.dropLast(1))
        tab != startTab -> copy(tab = startTab)
        else -> null
    }

    /** Replaces the top key; at a root, or with [key] already on top, returns this state. */
    fun replaceTop(key: NavKey): NavigationState {
        if (currentStack.size == 1 || currentStack.last() == key) return this
        require(key !in tabs) { "cannot push tab root $key" }
        return withCurrentStack(currentStack.dropLast(1) + key)
    }

    /**
     * Adapts this restored state to [fresh]'s tabs: keeps stacks whose root is still a tab,
     * adds missing tabs at their root, orders stacks like [fresh], and selects the start
     * tab when the selected tab is gone.
     */
    fun reconciledWith(fresh: NavigationState): NavigationState = NavigationState(
        tab = if (tab in fresh.tabs) tab else fresh.startTab,
        stacks = fresh.tabs.map { root -> stacks.firstOrNull { it.first() == root } ?: listOf(root) },
    )

    /** Spec row notation: the selected tab in bold, e.g. `**Home**[Home] Play[Play, Game]`. */
    override fun toString(): String = stacks.joinToString(" ") { stack ->
        val root = stack.first()
        val name = if (root == tab) "**$root**" else "$root"
        stack.joinToString(prefix = "$name[", postfix = "]")
    }

    private fun withCurrentStack(stack: List<NavKey>): NavigationState =
        copy(stacks = stacks.map { if (it.first() == tab) stack else it })
}

/** A fresh state: every tab at its root, the first tab selected and the start tab. */
fun navigationState(vararg tabs: NavKey): NavigationState =
    NavigationState(tab = tabs.first(), stacks = tabs.map { listOf(it) })
