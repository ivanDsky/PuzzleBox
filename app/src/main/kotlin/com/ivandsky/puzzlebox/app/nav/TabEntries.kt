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
 * Metadata key naming the tab whose stack an entry belongs to; its value is that tab's root
 * [NavKey]. Published contract: shell motion reads it to tell a tab switch from a push.
 */
internal const val OWNER_TAB = "puzzlebox.ownerTab"

/**
 * Metadata key carrying the entry's own [NavKey] (NavEntry does not expose it). Published
 * contract: shell motion reads it to pick a board's motion, and the scene canvas to tell an
 * immersive scene (NavTransitions.kt, NavSceneCanvas.kt).
 */
internal const val ENTRY_KEY = "puzzlebox.entryKey"

/**
 * The app's only Navigation3 adapter: decorates EVERY tab's stack (saveable state, a
 * per-entry ViewModel store, then the [SceneCanvas], then [shellChrome] when the shell
 * passes one), so an off-screen tab keeps its screens' state and a popped entry releases
 * its own. Content keys are namespaced by tab (the same key on two stacks is two entries)
 * and each entry carries [OWNER_TAB] and [ENTRY_KEY].
 *
 * Returns the entries NavDisplay renders: [NavigationState.visibleKeys]' entries, in order.
 */
@Composable
internal fun rememberTabEntries(
    state: NavigationState,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    shellChrome: NavEntryDecorator<NavKey>? = null,
): List<NavEntry<NavKey>> {
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStore = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val sceneCanvas = remember {
        NavEntryDecorator<NavKey> { entry -> SceneCanvas(entry.metadata[ENTRY_KEY]) { entry.Content() } }
    }
    val decorators = remember(saveableState, viewModelStore, sceneCanvas, shellChrome) {
        listOfNotNull(saveableState, viewModelStore, sceneCanvas, shellChrome)
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
    return state.visibleTabs.flatMap { decorated.getValue(it) }
}

private fun ownedBy(tab: NavKey, entry: NavEntry<NavKey>, navKey: NavKey): NavEntry<NavKey> {
    val metadata = buildMap(entry.metadata.size + 2) {
        putAll(entry.metadata)
        put(OWNER_TAB, tab)
        put(ENTRY_KEY, navKey)
    }
    return NavEntry(navKey, "$tab/${entry.contentKey}", metadata) { entry.Content() }
}
