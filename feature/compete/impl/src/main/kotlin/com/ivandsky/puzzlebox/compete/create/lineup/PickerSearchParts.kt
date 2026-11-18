package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The exact-puzzle field (paste when empty, clear otherwise; the keyboard's Search looks the code up) with the filter button beside it. */
@Composable
internal fun PickerSearchRow(state: AddPuzzleState, viewModel: AddPuzzleViewModel, modifier: Modifier = Modifier) {
    val text = state.query.text
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = viewModel::setText,
            modifier = Modifier.weight(1f).testTag("duel.lineup.search"),
            placeholder = { Text("Name, author or code", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                if (text.isEmpty()) {
                    PasteCodeButton(onPaste = viewModel::paste)
                } else {
                    IconButton(
                        onClick = { viewModel.setText("") },
                        modifier = Modifier.semantics { contentDescription = "Clear search" }.testTag("duel.lineup.search.clear"),
                    ) { Icon(PuzzleIcons.Close, contentDescription = null) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.lookUp() }),
        )
        PickerFilterButton(state.activeFilters, state.filtersOpen, onToggle = viewModel::toggleFilters)
    }
}

/**
 * The offline note, the code lookup's line (with Retry when it can be tried again) and, once the list has loaded, the
 * empty line or the count. Inline, with no column of its own: the caller's arrangement spaces them.
 */
@Composable
internal fun PickerStatusLines(state: AddPuzzleState, onRetry: () -> Unit) {
    if (state.offline && state.lookup != OFFLINE_NOTE) {
        Text(OFFLINE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.lineup.offline"))
    }
    state.lookup?.let {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("duel.lineup.lookup"))
            if (state.canRetry) TextButton(onClick = onRetry, modifier = Modifier.testTag("duel.lineup.lookup.retry")) { Text("Retry") }
        }
    }
    if (!state.loaded) return
    if (state.rows.isEmpty()) {
        // A lookup's own line says why there is nothing, better than "Nothing matches".
        if (state.lookup == null) {
            Text(emptyLine(state.query, state.pastYou), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("duel.lineup.empty"))
        }
    } else {
        Text(resultsLine(state.query, state.rows.size, state.pastYou), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("duel.lineup.count"))
    }
}
