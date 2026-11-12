package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
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
 * [onOpenReplay] reports the replay page of one solve of the open day, [onOpenCompare] the compare page of its first and latest.
 */
@Composable
fun HistoryRoute(
    key: HistoryKey,
    layout: HistoryLayout,
    onBack: (() -> Unit)?,
    onPlayAgain: (GameId, LocalDate) -> Unit,
    onOpenReplay: (ReplayKey) -> Unit,
    onOpenCompare: (ReplayCompareKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyDays = viewModel.pagedDays.collectAsLazyPagingItems()

    val replays by viewModel.replays.collectAsStateWithLifecycle()
    val replayHost = HistoryReplayHost(
        replays = replays,
        playUi = viewModel::playUiOf,
        onOpenDay = viewModel::openReplays,
        onOpen = { row, solve ->
            onOpenReplay(
                ReplayKey.daily(row.gameId.value, row.day.toString(), row.puzzleNumber, latest = solve == ReplaySolve.Latest),
            )
        },
        onCompare = { row -> onOpenCompare(historyCompareKey(row)) },
    )

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        state = uiState,
        onShowCalendar = viewModel::showCalendar,
        onSelectDay = viewModel::selectDay,
        onPlayAgain = onPlayAgain,
        replayHost = replayHost,
        modifier = modifier.testTag("route.history"),
    )
}
