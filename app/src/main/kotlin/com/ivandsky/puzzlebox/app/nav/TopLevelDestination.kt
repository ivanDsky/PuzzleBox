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

/** One bar/rail tab; [tag] is the item's test tag, so the bar never names a tab itself. */
data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
    val tag: String,
)

val topLevelDestinations = listOf(
    TopLevelDestination(HomeKey, "Home", Icons.Filled.Home, "nav.tab.home"),
    TopLevelDestination(PlayKey, "Play", Icons.Filled.PlayArrow, "nav.tab.play"),
    TopLevelDestination(ProfileKey, "Profile", Icons.Filled.Person, "nav.tab.profile"),
)
