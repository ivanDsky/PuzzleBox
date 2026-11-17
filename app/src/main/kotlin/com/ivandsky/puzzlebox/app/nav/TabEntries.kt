package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.ivandsky.puzzlebox.navigation.NavigationState

/**
 * Metadata key naming the stack an entry belongs to; its value is that tab's root [NavKey],
 * or [LAYER_OWNER] for an entry in the layer above the tabs. Published contract: shell motion
 * reads it to tell a tab switch, or the layer opening or closing, from a push.
 */
internal const val OWNER_TAB = "puzzlebox.ownerTab"

/** The [OWNER_TAB] value, and the content-key namespace, of the layer's entries: no tab owns them. */
internal const val LAYER_OWNER = "puzzlebox.layer"

/**
 * Metadata key carrying the entry's own [NavKey] (NavEntry does not expose it). Published
 * contract: shell motion reads it to pick a board's motion, and the scene canvas to tell an
 * immersive scene (NavTransitions.kt, NavSceneCanvas.kt).
 */
internal const val ENTRY_KEY = "puzzlebox.entryKey"

/**
 * The app's only Navigation3 adapter: decorates EVERY tab's stack and the layer above the tabs
 * (saveable state, a per-entry ViewModel store, then the [SceneCanvas], then [shellChrome]), so
 * an off-screen tab keeps its screens' state and a popped entry releases its own. Content keys
 * are namespaced by owner (the same key on two stacks is two entries) and each entry carries
 * [OWNER_TAB] and [ENTRY_KEY].
 *
 * Returns the entries NavDisplay renders: [NavigationState.visibleKeys]' entries, in order, so
 * an open layer's pages sit on top of the tab they cover.
 */
@Composable
internal fun rememberTabEntries(
    state: NavigationState,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    shellChrome: NavEntryDecorator<NavKey>,
): List<NavEntry<NavKey>> {
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStore = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val sceneCanvas = remember {
        NavEntryDecorator<NavKey> { entry -> SceneCanvas(entry.metadata[ENTRY_KEY]) { entry.Content() } }
    }
    val decorators = remember(saveableState, viewModelStore, sceneCanvas, shellChrome) {
        listOf(saveableState, viewModelStore, sceneCanvas, shellChrome)
    }
    val decorated = state.tabs.associateWith { tab ->
        key(tab) {
            rememberDecoratedNavEntries(
                backStack = state.stackOf(tab),
                entryDecorators = decorators,
                entryProvider = { navKey -> ownedBy(tab, entryProvider(navKey), navKey) },
            )
        }
    }
    val layer = key(LAYER_OWNER) {
        rememberDecoratedNavEntries(
            backStack = state.layer,
            entryDecorators = decorators,
            entryProvider = { navKey -> ownedBy(LAYER_OWNER, entryProvider(navKey), navKey) },
        )
    }
    return state.visibleTabs.flatMap { decorated.getValue(it) } + layer
}

/** [entry] as [owner]'s: a tab's root key, or [LAYER_OWNER]. */
private fun ownedBy(owner: Any, entry: NavEntry<NavKey>, navKey: NavKey): NavEntry<NavKey> {
    val metadata = buildMap(entry.metadata.size + 2) {
        putAll(entry.metadata)
        put(OWNER_TAB, owner)
        put(ENTRY_KEY, navKey)
    }
    return NavEntry(navKey, "$owner/${entry.contentKey}", metadata) { entry.Content() }
}
