package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val RESULTS_MAX_HEIGHT = 420.dp

/**
 * "Or one exact puzzle": a field that searches name, author and code, chips that narrow it, and the puzzles that pass, newest
 * first. [added] is what the lineup already holds; Add calls [onAdd] with the puzzle. Leaving the sheet clears the search.
 */
@Composable
internal fun ExactPuzzleSearch(
    games: List<DuelGame>,
    added: Set<LegRef>,
    onAdd: (ExactPuzzle) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddPuzzleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registry = koinInject<GamePlayRegistry>()
    var text by rememberSaveable { mutableStateOf(state.query.text) }
    LaunchedEffect(viewModel, added) { viewModel.setAdded(added) }
    DisposableEffect(viewModel) { onDispose(viewModel::reset) }
    Column(modifier.fillMaxWidth().testTag("duel.lineup.exact"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Or one exact puzzle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; viewModel.setText(it) },
            modifier = Modifier.fillMaxWidth().testTag("duel.lineup.search"),
            placeholder = { Text("Name, author or code (K7Q-X2M)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = { text = ""; viewModel.setText("") },
                        modifier = Modifier.semantics { contentDescription = "Clear search" }.testTag("duel.lineup.search.clear"),
                    ) { Icon(PuzzleIcons.Close, contentDescription = null) }
                }
            },
            singleLine = true,
        )
        PickerFilters(state.query, games, viewModel)
        if (state.offline) {
            Text(OFFLINE_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.lineup.offline"))
        }
        if (state.loaded) {
            if (state.rows.isEmpty()) {
                Text(emptyLine(state.query), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("duel.lineup.empty"))
            } else {
                Text(resultsLine(state.query, state.rows.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("duel.lineup.count"))
                LazyColumn(Modifier.heightIn(max = RESULTS_MAX_HEIGHT), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(state.rows, key = { it.puzzle.entry.ref.wire }) { entry ->
                        PickerRow(entry, registry.brandOf(entry.puzzle.entry.gameId), onAdd = { onAdd(entry.puzzle.entry) })
                    }
                }
            }
        }
    }
}
