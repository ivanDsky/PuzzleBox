package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

private val TouchTarget = 48.dp

/**
 * The row above New today's list when the filter is a sheet: the filter in force as a chip at the start (named in words,
 * a tap clears both picks), and at the end the filter icon that opens [CommunityFilterSheet]. The row is as tall with no
 * filter as with one, so the list does not jump when a filter is picked or cleared.
 */
@Composable
internal fun CommunityFilterBar(state: CommunityState, actions: CommunityActions, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().heightIn(min = TouchTarget), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            activeFilterLabel(state)?.let { ActiveFilterChip(it, actions.onClearFilters) }
        }
        IconButton(onClick = { open = true }, modifier = Modifier.size(TouchTarget).testTag("community.filter.button")) {
            Icon(PuzzleIcons.Filter, contentDescription = "Filter puzzles", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        CommunityFilterSheet(state.games, state.game, state.difficulty, actions.onGame, actions.onDifficulty, onDismiss = { open = false })
    }
}

/** A selected chip with [label], "Zip · Hard"; a tap anywhere on it (its × says so) calls [onClear]. */
@Composable
private fun ActiveFilterChip(label: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.testTag("community.filter.active"),
        trailingIcon = { Icon(PuzzleIcons.Close, contentDescription = "Clear filter", modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    )
}
