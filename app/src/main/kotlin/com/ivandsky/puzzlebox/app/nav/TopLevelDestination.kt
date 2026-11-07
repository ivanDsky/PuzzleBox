package com.ivandsky.puzzlebox.app.nav

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey

/**
 * One bar/rail tab; [tag] is the item's test tag, so the bar never names a tab itself.
 * [icon] is the outlined unselected state, [selectedIcon] the filled selected one (N5).
 */
data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val tag: String,
)

val topLevelDestinations = listOf(
    TopLevelDestination(HomeKey, "Home", PuzzleIcons.Home, PuzzleIcons.HomeFilled, "nav.tab.home"),
    TopLevelDestination(PlayKey, "Play", PuzzleIcons.Box, PuzzleIcons.BoxFilled, "nav.tab.play"),
    TopLevelDestination(CreateKey, "Create", PuzzleIcons.Create, PuzzleIcons.CreateFilled, "nav.tab.create"),
    TopLevelDestination(ProfileKey, "Profile", PuzzleIcons.Profile, PuzzleIcons.ProfileFilled, "nav.tab.profile"),
)
