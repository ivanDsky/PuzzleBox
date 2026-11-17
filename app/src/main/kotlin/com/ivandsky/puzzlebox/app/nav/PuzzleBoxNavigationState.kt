package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.navigationState

/**
 * The app's fresh navigation: every tab of [destinations] at its root, in order, so the first
 * destination (Today) is selected and is the start tab back exits through. The shell passes
 * the app's tabs.
 */
internal fun puzzleBoxNavigationState(
    destinations: List<TopLevelDestination> = topLevelDestinations,
): NavigationState = navigationState(*destinations.map { it.key }.toTypedArray())
