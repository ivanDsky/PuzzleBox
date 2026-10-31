package com.ivandsky.puzzlebox.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object Play : NavKey

/** Full history browser over the captured days (A8) — pushed from Home's teaser. */
@Serializable
data object History : NavKey

/**
 * Replay of a captured day (ticket 14): the history-built session blob rides base64
 * in the key; the play entry restores it via its existing restore path. The blob is
 * self-describing (A19) and carries the day's ref, so records stay attributed to
 * their day (A4).
 */
@Serializable
data class ReplayZip(val session: String) : NavKey

@Serializable
data object Profile : NavKey

// Detail route under Profile: the design-system catalog.
@Serializable
data object Catalog : NavKey

data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Home, "Home", Icons.Filled.Home),
    TopLevelDestination(Play, "Play", Icons.Filled.PlayArrow),
    TopLevelDestination(Profile, "Profile", Icons.Filled.Person),
)
