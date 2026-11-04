package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * The saved navigation's version key: the encoded [NavigationState] is stored under it, so a
 * bundle without it (the 0.8 single `NavBackStack`) is recognised as foreign and dropped.
 */
internal const val NAVIGATION_STATE_KEY = "puzzlebox.navigation.v1"

/**
 * The app's [Navigator], surviving rotation and process death. A restored state is
 * reconciled with [fresh] (tabs added or dropped since it was saved); a bundle that is
 * missing, foreign or undecodable, or breaks a state invariant, starts at [fresh] (A17).
 */
@Composable
internal fun rememberNavigator(fresh: NavigationState = puzzleBoxNavigationState()): Navigator =
    rememberSaveable(saver = navigatorSaver(fresh)) { Navigator(fresh) }

private fun navigatorSaver(fresh: NavigationState): Saver<Navigator, SavedState> = Saver(
    save = { navigator ->
        savedState {
            putSavedState(
                NAVIGATION_STATE_KEY,
                encodeToSavedState(NavigationState.serializer(), navigator.state, puzzleBoxNavKeys),
            )
        }
    },
    restore = { saved -> Navigator(restoredState(saved, fresh)) },
)

private fun restoredState(saved: SavedState, fresh: NavigationState): NavigationState =
    runCatching {
        val encoded = saved.read { getSavedStateOrNull(NAVIGATION_STATE_KEY) } ?: return fresh
        decodeFromSavedState(NavigationState.serializer(), encoded, puzzleBoxNavKeys).reconciledWith(fresh)
    }.getOrDefault(fresh)
