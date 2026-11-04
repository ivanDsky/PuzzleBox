package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.navigationState

/**
 * The app's fresh navigation: every tab at its root in [topLevelDestinations] order, so the
 * first destination (Home) is selected and is the start tab back exits through.
 */
internal fun puzzleBoxNavigationState(): NavigationState =
    navigationState(*topLevelDestinations.map { it.key }.toTypedArray())
