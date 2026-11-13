package com.ivandsky.puzzlebox.history.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.history.historyCompareKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.ranks.RanksScreen
import com.ivandsky.puzzlebox.history.ranks.RanksViewModel
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * The Ranks hub's route for the tab root [HistoryRootKey], the successor of [historyEntry]'s root
 * entry. The module exports both; the app picks one by flag, so this feature never reads it
 * (A27). Same receiver and parameters as [historyEntry]: [navigator] opens a day's game and its
 * replays from the Your solve row; [layout] is what the tablet pane reads once it exists.
 */
fun EntryProviderScope<NavKey>.ranksEntry(
    navigator: Navigator,
    layout: HistoryLayout,
) {
    entry<HistoryRootKey> {
        val viewModel = koinViewModel<RanksViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        RanksScreen(
            state = state,
            onSignIn = { scope.launch { viewModel.signIn(context) } },
            onSwipe = viewModel::swipe,
            onShowCalendar = viewModel::showCalendar,
            onSelectDay = viewModel::selectDay,
            onPlayAgain = { game, day -> navigator.pushOnce(GameKey(game.value, day.toString())) },
            onOpenReplay = { row, solve ->
                navigator.pushOnce(ReplayKey.daily(row.gameId.value, row.day.toString(), row.puzzleNumber, latest = solve == ReplaySolve.Latest))
            },
            onCompare = { row -> navigator.pushOnce(historyCompareKey(row)) },
        )
    }
}
