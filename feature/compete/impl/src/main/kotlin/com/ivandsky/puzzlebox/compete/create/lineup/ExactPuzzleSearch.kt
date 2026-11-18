package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject

private val RESULTS_MAX_HEIGHT = 420.dp

/**
 * "Or one exact puzzle": a field that searches name, author and code, a filter button right of it that opens the chips
 * that narrow it under the field, and the puzzles that pass, newest first (Past you's: the ones solved last first). [added] is what the lineup already
 * holds; Add calls [onAdd] with the puzzle. The query and whether the chips are open live in [viewModel], so the field, the rows and the chips all survive a
 * rotation; the panel clears them when the sheet closes.
 */
@Composable
internal fun ExactPuzzleSearch(
    games: List<DuelGame>,
    added: Set<LegRef>,
    onAdd: (ExactPuzzle) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddPuzzleViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registry = koinInject<GamePlayRegistry>()
    LaunchedEffect(viewModel, added) { viewModel.setAdded(added) }
    Column(modifier.fillMaxWidth().testTag("duel.lineup.exact"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Or one exact puzzle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            PickerSearchRow(state, viewModel)
            PickerFilterPanel(state, games, viewModel)
        }
        PickerStatusLines(state, onRetry = viewModel::lookUp)
        if (state.loaded && state.rows.isNotEmpty()) {
            val listState = rememberLazyListState()
            // Only once the player has scrolled, so a list that fits never pulls pages the player did not ask for.
            LaunchedEffect(listState, viewModel) {
                snapshotFlow { listState.canScrollBackward && !listState.canScrollForward }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
            }
            LazyColumn(Modifier.heightIn(max = RESULTS_MAX_HEIGHT), state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.rows, key = { it.puzzle.entry.ref.wire }) { entry ->
                    PickerRow(entry, registry.brandOf(entry.puzzle.entry.gameId), onAdd = { onAdd(entry.puzzle.entry) })
                }
            }
        }
    }
}
