package com.ivandsky.puzzlebox.app.nav

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * The bottom bar for the single-pane shapes: one item per [topLevelDestinations] entry,
 * tagged with [TopLevelDestination.tag]; the item whose key is [selected] carries the
 * selected semantics. Items whose key is in [badged] carry a primary-coloured badge dot at
 * the icon's top end, except the selected item, whose screen already shows the cue (A13,
 * SC11); the caller decides which keys, so the bar never names a tab.
 */
@Composable
internal fun TopLevelNavBar(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
    badged: Set<NavKey> = emptySet(),
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination.key,
                onClick = { onSelect(destination.key) },
                icon = { TabIcon(destination, selected == destination.key, showsBadge(destination, selected, badged)) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag(destination.tag),
            )
        }
    }
}

/** The rail for the two-pane shape; same items, tags, selection and badges as [TopLevelNavBar]. */
@Composable
internal fun TopLevelNavRail(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
    badged: Set<NavKey> = emptySet(),
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        topLevelDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination.key,
                onClick = { onSelect(destination.key) },
                icon = { TabIcon(destination, selected == destination.key, showsBadge(destination, selected, badged)) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag(destination.tag),
            )
        }
    }
}

/** Content description of the badge dot, announced alongside the tab label. */
internal const val IN_PROGRESS_BADGE_DESCRIPTION = "Game in progress"

/** Test tag of a badge dot: `<tab tag>.badge`. */
internal fun badgeTag(destination: TopLevelDestination): String = "${destination.tag}.badge"

/** The dot shows on a badged tab only while another tab is selected. */
private fun showsBadge(destination: TopLevelDestination, selected: NavKey, badged: Set<NavKey>): Boolean =
    destination.key in badged && destination.key != selected

/** The tab's icon: filled while [selected], outlined otherwise (N5). */
@Composable
private fun TabIcon(destination: TopLevelDestination, selected: Boolean, badged: Boolean) {
    val icon = if (selected) destination.selectedIcon else destination.icon
    if (badged) {
        BadgedBox(
            badge = {
                Badge(
                    modifier = Modifier
                        .testTag(badgeTag(destination))
                        .semantics { contentDescription = IN_PROGRESS_BADGE_DESCRIPTION },
                    containerColor = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            Icon(icon, contentDescription = null)
        }
    } else {
        Icon(icon, contentDescription = null)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TopLevelNavBarPreview() {
    PuzzleBoxTheme {
        TopLevelNavBar(selected = HomeKey, onSelect = {}, badged = setOf(PlayKey))
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TopLevelNavRailPreview() {
    PuzzleBoxTheme {
        TopLevelNavRail(selected = HomeKey, onSelect = {}, badged = setOf(PlayKey))
    }
}
