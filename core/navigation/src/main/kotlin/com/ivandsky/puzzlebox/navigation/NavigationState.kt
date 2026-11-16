package com.ivandsky.puzzlebox.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The selected [tab] plus one back stack per tab; each stack's first key is its tab root.
 * The first stack's root is the [startTab] the app exits through. [layer] is one more stack
 * that covers the tabs while it holds a key and belongs to no tab; empty means closed.
 *
 * A pure value: every verb returns a new state and never mutates this one. Invariants are
 * checked on construction and on decode, so a corrupt restored state fails fast:
 * - I1: there are stacks and none is empty;
 * - I2: roots are distinct;
 * - I3: [tab] is one of [tabs];
 * - I4: a root appears only at index 0 of its own stack;
 * - I5: [layer] holds no tab root and no key that is on a stack.
 *
 * Keys serialize polymorphically over [NavKey]; decoding needs the app's NavKey module.
 * [layer] is defaulted, so a state saved before it existed decodes with the layer closed.
 */
@Immutable
@Serializable
data class NavigationState(
    val tab: NavKey,
    val stacks: List<List<NavKey>>,
    val layer: List<NavKey> = emptyList(),
) {

    init {
        require(stacks.isNotEmpty() && stacks.none { it.isEmpty() }) { "I1: empty stacks in $stacks" }
        require(stacks.map { it.first() }.toSet().size == stacks.size) { "I2: duplicate roots in $stacks" }
        require(stacks.any { it.first() == tab }) { "I3: $tab is not a tab of $stacks" }
        val roots = stacks.map { it.first() }.toSet()
        require(stacks.all { stack -> stack.drop(1).none { it in roots } }) {
            "I4: a tab root sits above index 0 in $stacks"
        }
        require(layer.none { key -> stacks.any { key in it } }) { "I5: layer $layer shares a key with $stacks" }
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

    /** Keys rendered, bottom to top, the layer above the tabs; the last one is what back removes. */
    val visibleKeys: List<NavKey> get() = visibleTabs.flatMap(::stackOf) + layer

    val allKeys: Set<NavKey> get() = (stacks.flatten() + layer).toSet()

    /**
     * Selects [tab] and closes the layer. With no layer open, reselecting the current tab pops
     * it to its root; with the layer open it only closes the layer.
     */
    fun selectTab(tab: NavKey): NavigationState {
        stackOf(tab)
        return when {
            layer.isNotEmpty() -> copy(tab = tab, layer = emptyList())
            tab == this.tab -> withCurrentStack(listOf(tab))
            else -> copy(tab = tab)
        }
    }

    /** Pushes [key] on the current stack, closing the layer first; a tab root cannot be pushed. */
    fun push(key: NavKey): NavigationState {
        require(key !in tabs) { "cannot push tab root $key" }
        return copy(stacks = stacks.map { if (it.first() == tab) it + key else it }, layer = emptyList())
    }

    /** [push], but with [key] already on top of the current stack it only closes the layer. */
    fun pushOnce(key: NavKey): NavigationState = when {
        currentStack.last() != key -> push(key)
        layer.isEmpty() -> this
        else -> copy(layer = emptyList())
    }

    /** Opens the layer at [key], replacing any pages it held. A tab root cannot enter the layer. */
    fun openLayer(key: NavKey): NavigationState = withLayer(listOf(key))

    /** Pushes [key] on top of the layer, opening it when closed; a no-op when [key] is already on top. */
    fun pushLayer(key: NavKey): NavigationState = if (layer.lastOrNull() == key) this else withLayer(layer + key)

    /**
     * Removes the top key: the layer's while it is open, else the current stack's. At a
     * non-start root it selects the start tab and keeps both stacks; at the start root it
     * returns null (the app exits).
     */
    fun pop(): NavigationState? = when {
        layer.isNotEmpty() -> copy(layer = layer.dropLast(1))
        currentStack.size > 1 -> withCurrentStack(currentStack.dropLast(1))
        tab != startTab -> copy(tab = startTab)
        else -> null
    }

    /**
     * Replaces the top key, of the layer while it is open, else of the current stack; at a
     * root, or with [key] already on top, returns this state.
     */
    fun replaceTop(key: NavKey): NavigationState {
        if (layer.isNotEmpty()) return if (layer.last() == key) this else withLayer(layer.dropLast(1) + key)
        if (currentStack.size == 1 || currentStack.last() == key) return this
        require(key !in tabs) { "cannot push tab root $key" }
        return withCurrentStack(currentStack.dropLast(1) + key)
    }

    /**
     * Adapts this restored state to [fresh]'s tabs: keeps stacks whose root is still a tab,
     * adds missing tabs at their root, orders stacks like [fresh], and selects the start
     * tab when the selected tab is gone. The layer stays open, unless one of its keys is
     * now a tab root: then it closes.
     */
    fun reconciledWith(fresh: NavigationState): NavigationState = NavigationState(
        tab = if (tab in fresh.tabs) tab else fresh.startTab,
        stacks = fresh.tabs.map { root -> stacks.firstOrNull { it.first() == root } ?: listOf(root) },
        layer = if (layer.any { it in fresh.tabs }) emptyList() else layer,
    )

    /**
     * Spec row notation: the selected tab in bold, then the layer when it is open, e.g.
     * `**Home**[Home] Play[Play, Game] Layer[Profile, Settings]`.
     */
    override fun toString(): String {
        val tabStacks = stacks.joinToString(" ") { stack ->
            val root = stack.first()
            val name = if (root == tab) "**$root**" else "$root"
            stack.joinToString(prefix = "$name[", postfix = "]")
        }
        return if (layer.isEmpty()) tabStacks else tabStacks + layer.joinToString(prefix = " Layer[", postfix = "]")
    }

    private fun withCurrentStack(stack: List<NavKey>): NavigationState =
        copy(stacks = stacks.map { if (it.first() == tab) stack else it })

    /** This state with [layer]; a key the layer takes leaves any stack it was on (I5). */
    private fun withLayer(layer: List<NavKey>): NavigationState {
        require(layer.none { it in tabs }) { "cannot put a tab root in the layer: $layer" }
        return copy(stacks = stacks.map { stack -> stack.filterNot { it in layer } }, layer = layer)
    }
}

/** A fresh state: every tab at its root, the first tab selected and the start tab. */
fun navigationState(vararg tabs: NavKey): NavigationState =
    NavigationState(tab = tabs.first(), stacks = tabs.map { listOf(it) })
