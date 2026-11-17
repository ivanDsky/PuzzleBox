package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

private val TouchTarget = 48.dp

/** The filter in words, "Zip · Hard", from the picks in [state]; null when nothing is picked. */
internal fun activeFilterLabel(state: CommunityState): String? {
    val game = state.games.firstOrNull { it.id == state.game }?.name
    return listOfNotNull(game, state.difficulty?.label).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The line under the header while a filter is on: [label] then a 48dp × that clears it through [onClear]. */
@Composable
internal fun CommunityActiveFilter(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("community.filter.active"),
        )
        IconButton(onClick = onClear, modifier = Modifier.size(TouchTarget).testTag("community.filter.clear")) {
            Icon(PuzzleIcons.Close, contentDescription = "Clear filter", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
