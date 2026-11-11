package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.navigationState

/**
 * The app's fresh navigation: every tab of [topLevelDestinations] at its root,
 * in order, so the first destination (Today) is selected and is the start tab back exits
 * through.
 */
internal fun puzzleBoxNavigationState(): NavigationState =
    navigationState(*topLevelDestinations.map { it.key }.toTypedArray())
