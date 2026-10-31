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
