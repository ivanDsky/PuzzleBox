package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * History's DI-bound entry point: renders [HistoryViewModel]'s state and paged rows;
 * each [key] gets its own ViewModel. A key without a game opens the All timeline (A28); one
 * with a game opens on that game's day (see [HistoryViewModel]). The pane layout is decided
 * once at the app shell (ticket 07 policy) and passed in; UI below this point stays
 * repository-free.
 *
 * Play again (ticket 11): [onPlayAgain] reports the (game, day) played again; the shell opens that
 * day's game slot, which the play store builds or resumes.
 */
@Composable
fun HistoryRoute(
    key: HistoryKey,
    layout: HistoryLayout,
    onBack: (() -> Unit)?,
    onPlayAgain: (GameId, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyDays = viewModel.pagedDays.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        state = uiState,
        onShowCalendar = viewModel::showCalendar,
        onSelectDay = viewModel::selectDay,
        onPlayAgain = onPlayAgain,
        modifier = modifier.testTag("route.history"),
    )
}
