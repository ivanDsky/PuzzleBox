package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.navigationState

/**
 * The app's fresh navigation: every tab of [topLevelDestinations] for [navV2] at its root,
 * in order, so the first destination (Home, or Today with the flag on) is selected and is
 * the start tab back exits through.
 */
internal fun puzzleBoxNavigationState(navV2: Boolean = false): NavigationState =
    navigationState(*topLevelDestinations(navV2).map { it.key }.toTypedArray())
