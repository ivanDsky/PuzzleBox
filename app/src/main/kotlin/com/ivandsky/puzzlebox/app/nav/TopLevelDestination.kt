package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Ranks
import com.ivandsky.puzzlebox.designsystem.RanksFilled
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * One bar/rail tab; [tag] is the item's test tag, so the bar never names a tab itself.
 * [icon] is the outlined unselected state, [selectedIcon] the filled selected one (N5);
 * both resolve in composition, because a drawable-backed icon has a
 * `@Composable` getter. [tab] is the [Tab] this destination roots, the one join from
 * a [Tab] to its key.
 */
internal data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: @Composable () -> ImageVector,
    val selectedIcon: @Composable () -> ImageVector,
    val tag: String,
    val tab: Tab,
)

private val home = TopLevelDestination(
    HomeKey, "Today", { PuzzleIcons.Home }, { PuzzleIcons.HomeFilled }, "nav.tab.home", Tab.Today,
)
private val play = TopLevelDestination(
    PlayKey, "Games", { PuzzleIcons.Box }, { PuzzleIcons.BoxFilled }, "nav.tab.play", Tab.Games,
)
private val ranks = TopLevelDestination(
    HistoryRootKey, "Ranks", { PuzzleIcons.Ranks }, { PuzzleIcons.RanksFilled }, "nav.tab.ranks", Tab.History,
)
private val community = TopLevelDestination(
    CommunityKey, "Community", { PuzzleIcons.Community }, { PuzzleIcons.CommunityFilled }, "nav.tab.community", Tab.Community,
)

/**
 * The base tab set: Today, Games, Ranks (ADR-0027, amended). Making puzzles lives in Games, so Create is not a tab;
 * its key stays registered so a state saved with four tabs still decodes, and restore drops its stack. Ranks took
 * History's slot and keeps its key and [Tab], so a saved back stack, the placement table and [openOwner] read the
 * same as they did when the third tab was History.
 */
internal val topLevelDestinations: List<TopLevelDestination> = listOf(home, play, ranks)

/**
 * The tab set with CommunityPuzzles on (nav-rethink M10-1): Today, Games, Community, Ranks. Community takes Custom's
 * list out of Games.
 */
internal val communityDestinations: List<TopLevelDestination> = listOf(home, play, community, ranks)
