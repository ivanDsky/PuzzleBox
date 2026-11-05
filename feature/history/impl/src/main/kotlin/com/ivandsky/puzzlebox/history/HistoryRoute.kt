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
 * History's DI-bound entry point for [key]: renders [HistoryViewModel]'s state, tab and
 * paged rows; each key gets its own ViewModel, which opens on the key's game tab. The
 * pane layout is decided once at the app shell (ticket 07 policy) and passed in; UI
 * below this point stays repository-free.
 *
 * Replay (ticket 11): [onReplay] reports the replayed (game, day); the shell opens that
 * day's game slot, which the play store builds or resumes.
 */
@Composable
fun HistoryRoute(
    key: HistoryKey,
    layout: HistoryLayout,
    onBack: () -> Unit,
    onReplay: (GameId, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val lazyDays = viewModel.pagedDays.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        state = uiState,
        selectedTab = selectedTab,
        onSelectTab = viewModel::select,
        onShowCalendar = viewModel::showCalendar,
        onSelectDay = viewModel::selectDay,
        onReplay = onReplay,
        modifier = modifier.testTag("route.history"),
    )
}
