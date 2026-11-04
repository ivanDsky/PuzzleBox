package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import org.koin.androidx.compose.koinViewModel

/**
 * History's DI-bound entry point: renders [HistoryViewModel]'s state and paged rows.
 * The pane layout is decided once at the app shell (ticket 07 policy) and passed in; UI
 * below this point stays repository-free.
 *
 * Replay (ticket 14 → 13, generalized in tango-05): a ready replay hands the play route
 * a self-describing [com.ivandsky.puzzlebox.game.SessionBlob]; [onReplay] is the shell's
 * navigation into the play entry.
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    onReplay: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyDays = viewModel.pagedDays.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        allRows = uiState.rows,
        recordsByKey = uiState.recordsByKey,
        streak = uiState.streak,
        events = viewModel.events,
        onReplayDay = viewModel::onReplay,
        onReplay = onReplay,
        modifier = modifier,
    )
}
