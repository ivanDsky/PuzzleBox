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

/**
 * The Queens route (queens-10): the Play tab's active game — the tab resolves to this
 * entry, which hosts the queens-ui board off the Koin graph. Zip's daily stays at [Play],
 * pushed from Home's Today card; replay stays [ReplayZip].
 */
@Serializable
data object Queens : NavKey

/**
 * Solved-session result (ticket 23, spec UIUX-01 A3/A9): a proper route, not an overlay.
 * The frozen A3 facts ride the key so the surface renders even after process death; the
 * record badge is live flow state — the record write is fire-and-observe, never awaited.
 * Solving replaces everything above Home with this entry, so the system back (a plain
 * pop) lands on Home — back never surprises (senior-UX wayfinding).
 */
@Serializable
data class Result(val timeMs: Long, val hintsUsed: Int) : NavKey

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
    TopLevelDestination(Queens, "Play", Icons.Filled.PlayArrow),
    TopLevelDestination(Profile, "Profile", Icons.Filled.Person),
)
