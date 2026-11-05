package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel

/**
 * History's DI-bound entry point: renders [HistoryViewModel]'s state and paged rows.
 * The pane layout is decided once at the app shell (ticket 07 policy) and passed in; UI
 * below this point stays repository-free.
 *
 * Replay (ticket 11): [onReplay] reports the replayed (game, day); the shell opens that
 * day's game slot, which the play store builds or resumes.
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    onReplay: (GameId, LocalDate) -> Unit,
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
        onReplay = onReplay,
        modifier = modifier.testTag("route.history"),
    )
}
