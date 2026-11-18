package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject

private const val TILES_PER_ROW = 2
private const val PANEL_INDEX = 2

/**
 * The Add / Change puzzle page of the New duel screen: one lazy list that fills the height. The game tiles lead it; the
 * exact-puzzle field and its filter button stay at the top once the tiles have scrolled away; the filter panel follows
 * them in the list, and opening it scrolls it into view, so on a small phone with the keyboard up it never pins the
 * rows out of sight. Then come the status lines and the puzzles that pass the search. A tile or an Add calls [onPick] with what it picks; the caller
 * closes the page. [viewModel] is the search over what a friend could be asked to play, or, for Past you, over what the
 * player has solved; it keeps the query and the open panel, and the caller resets it when the page closes.
 * The list pads for the keyboard, so the last row can be reached with it up. A non-null [onClose] is for a page that
 * sits beside the form: it adds a line over the list with the row's title and a Close button.
 */
@Composable
internal fun PuzzlePickerPage(
    picker: LineupPickerModel,
    onPick: (LineupEntry) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    viewModel: AddPuzzleViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registry = koinInject<GamePlayRegistry>()
    val listState = rememberLazyListState()
    var headerPx by remember { mutableIntStateOf(0) }
    var panelOpen by remember { mutableStateOf(state.filtersOpen) }
    LaunchedEffect(viewModel, picker.added) { viewModel.setAdded(picker.added) }
    // Opening the panel brings it to rest just under the header, not behind it. A page that restores already open stays where it was.
    LaunchedEffect(listState, state.filtersOpen) {
        if (state.filtersOpen && !panelOpen) listState.animateScrollToItem(PANEL_INDEX, scrollOffset = -headerPx)
        panelOpen = state.filtersOpen
    }
    // Only once the player has scrolled, so a list that fits never pulls pages the player did not ask for.
    LaunchedEffect(listState, viewModel) {
        snapshotFlow { listState.canScrollBackward && !listState.canScrollForward }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }
    Column(modifier.fillMaxSize()) {
        if (onClose != null) PickerHeader(picker.title(), onClose)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().imePadding().testTag("duel.lineup.picker"),
            state = listState,
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "tiles") {
                Column(Modifier.padding(top = Spacing.md)) {
                    GameTiles(picker.games, picker.solved, TILES_PER_ROW, onPick = { onPick(AnyGame(it)) })
                }
            }
            stickyHeader(key = "search") {
                // Opaque, so the rows that scroll under it do not show through.
                Column(
                    Modifier.fillMaxWidth().testTag("duel.lineup.exact").onSizeChanged { headerPx = it.height }
                        .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("Or one exact puzzle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PickerSearchRow(state, viewModel)
                }
            }
            if (state.filtersOpen) item(key = "filters") { PickerFilterPanel(state, picker.games, viewModel) }
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { PickerStatusLines(state, onRetry = viewModel::lookUp) }
            }
            if (state.loaded) {
                items(state.rows, key = { it.puzzle.entry.ref.wire }) { entry ->
                    PickerRow(entry, registry.brandOf(entry.puzzle.entry.gameId), onAdd = { onPick(entry.puzzle.entry) })
                }
            }
        }
    }
}

/** "Add puzzle 3" or "Change puzzle 3": which row the page is for. */
internal fun LineupPickerModel.title() = if (changing) "Change puzzle $position" else "Add puzzle $position"

/** The line over the page when it sits beside the form, where the screen's own bar still reads "New duel". */
@Composable
private fun PickerHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.testTag("duel.lineup.picker.close")) { Icon(PuzzleIcons.Close, contentDescription = "Close") }
    }
}
