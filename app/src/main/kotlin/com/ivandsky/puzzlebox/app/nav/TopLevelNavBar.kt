package com.ivandsky.puzzlebox.app.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * The bottom bar for the single-pane shapes: one item per [topLevelDestinations] entry,
 * tagged with [TopLevelDestination.tag]; the item whose key is [selected] carries the
 * selected semantics.
 */
@Composable
internal fun TopLevelNavBar(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination.key,
                onClick = { onSelect(destination.key) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag(destination.tag),
            )
        }
    }
}

/** The rail for the two-pane shape; same items, tags and selection as [TopLevelNavBar]. */
@Composable
internal fun TopLevelNavRail(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        topLevelDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selected == destination.key,
                onClick = { onSelect(destination.key) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag(destination.tag),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TopLevelNavBarPreview() {
    PuzzleBoxTheme {
        TopLevelNavBar(selected = PlayKey, onSelect = {})
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TopLevelNavRailPreview() {
    PuzzleBoxTheme {
        TopLevelNavRail(selected = PlayKey, onSelect = {})
    }
}
