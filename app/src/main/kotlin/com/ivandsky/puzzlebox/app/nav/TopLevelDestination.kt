package com.ivandsky.puzzlebox.app.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey

data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(HomeKey, "Home", Icons.Filled.Home),
    TopLevelDestination(PlayKey, "Play", Icons.Filled.PlayArrow),
    TopLevelDestination(ProfileKey, "Profile", Icons.Filled.Person),
)
