package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey

/**
 * One bar/rail tab; [tag] is the item's test tag, so the bar never names a tab itself.
 * [icon] is the outlined unselected state, [selectedIcon] the filled selected one (N5);
 * both resolve in composition, because a drawable-backed icon (History) has a
 * `@Composable` getter. [tab] is the NavV2 [Tab] this destination roots, the one join from
 * a [Tab] to its key; null for a destination that is no NavV2 tab (Profile, with the flag
 * off).
 */
internal data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: @Composable () -> ImageVector,
    val selectedIcon: @Composable () -> ImageVector,
    val tag: String,
    val tab: Tab? = null,
)

// Shared by both sets, and declared before `topLevelDestinations`: top-level properties
// initialise in file order.
private val home = TopLevelDestination(
    HomeKey, "Home", { PuzzleIcons.Home }, { PuzzleIcons.HomeFilled }, "nav.tab.home", Tab.Today,
)
private val play = TopLevelDestination(
    PlayKey, "Play", { PuzzleIcons.Box }, { PuzzleIcons.BoxFilled }, "nav.tab.play", Tab.Games,
)
private val create = TopLevelDestination(
    CreateKey, "Create", { PuzzleIcons.Create }, { PuzzleIcons.CreateFilled }, "nav.tab.create", Tab.Create,
)

private val flagOff = listOf(
    home,
    play,
    create,
    TopLevelDestination(ProfileKey, "Profile", { PuzzleIcons.Profile }, { PuzzleIcons.ProfileFilled }, "nav.tab.profile"),
)

private val flagOn = listOf(
    home.copy(label = "Today"),
    play.copy(label = "Games"),
    TopLevelDestination(
        HistoryRootKey, "History", { PuzzleIcons.History }, { PuzzleIcons.History }, "nav.tab.history", Tab.History,
    ),
    create,
)

/** The flag-off tab set: Home, Play, Create, Profile. */
internal val topLevelDestinations: List<TopLevelDestination> = flagOff

/**
 * The tab set for a [navV2] value. Off: Home, Play, Create, Profile. On: Today, Games,
 * History, Create; Today and Games keep Home's and Play's keys, icons and tags, and History
 * has one icon for both states.
 */
internal fun topLevelDestinations(navV2: Boolean): List<TopLevelDestination> = if (navV2) flagOn else flagOff
