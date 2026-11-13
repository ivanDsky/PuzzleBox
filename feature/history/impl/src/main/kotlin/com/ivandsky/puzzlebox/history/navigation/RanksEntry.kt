package com.ivandsky.puzzlebox.history.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.history.historyCompareKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel
import com.ivandsky.puzzlebox.history.ranks.RanksBoard
import com.ivandsky.puzzlebox.history.ranks.RanksSelection
import com.ivandsky.puzzlebox.history.ranks.RanksScreen
import com.ivandsky.puzzlebox.history.ranks.RanksViewModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

/**
 * The Ranks hub's routes, the successor of [historyEntry]: the tab root [HistoryRootKey] and the pushed [HistoryKey]
 * (a game's link, with a back arrow), which opens the hub at that game and day (A4) where [historyEntry] opens the history
 * browser. The module exports both; the app picks one by flag, so this feature never reads it (A27). Same receiver and
 * parameters as [historyEntry]: [navigator] opens a day's game and its replays from the Your solve row; [layout] picks
 * the tablet's two panes. [linkedIn] adds the connections' ghosts to a game's Friends day board, from what the phone
 * holds; off, the hub touches nothing of LinkedIn.
 */
fun EntryProviderScope<NavKey>.ranksEntry(
    navigator: Navigator,
    layout: HistoryLayout,
    linkedIn: Boolean = false,
) {
    entry<HistoryRootKey> { RanksRoute(navigator, layout, linkedIn, RanksSelection(), key = null, onBack = null) }
    entry<HistoryKey> { key ->
        val opening = RanksSelection.opening(
            board = key.gameId?.let { RanksBoard.Game(GameId(it)) } ?: RanksBoard.Overall,
            day = key.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
        RanksRoute(navigator, layout, linkedIn, opening, key = key.toString(), onBack = { navigator.pop() })
    }
}

/** One hub per entry: [key] keeps a pushed hub's state apart from the root's; [opening] is where a fresh one starts. */
@Composable
private fun RanksRoute(
    navigator: Navigator,
    layout: HistoryLayout,
    linkedIn: Boolean,
    opening: RanksSelection,
    key: String?,
    onBack: (() -> Unit)?,
) {
    val viewModel = koinViewModel<RanksViewModel>(key = key, parameters = { parametersOf(linkedIn, opening) })
    val playerSheet = koinViewModel<PlayerSheetViewModel>(key = key)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    RanksScreen(
        state = state,
        layout = layout,
        onBack = onBack,
        onReset = viewModel::resetSelection,
        onSignIn = { scope.launch { viewModel.signIn(context) } },
        onSwipe = viewModel::swipe,
        onPickBoard = viewModel::pickBoard,
        onNudgeShown = viewModel::markSwipeNudgeSeen,
        onScope = viewModel::setScope,
        onWindow = viewModel::setWindow,
        onShowCalendar = viewModel::showCalendar,
        onSelectDay = viewModel::selectDay,
        onPlayAgain = { game, day -> navigator.pushOnce(GameKey(game.value, day.toString())) },
        onOpenReplay = { row, solve ->
            navigator.pushOnce(ReplayKey.daily(row.gameId.value, row.day.toString(), row.puzzleNumber, latest = solve == ReplaySolve.Latest))
        },
        onCompare = { row -> navigator.pushOnce(historyCompareKey(row)) },
        onWatchPlayer = { target ->
            target.board?.let { navigator.pushOnce(ReplayKey.player(it.gameId, it.day.toString(), target.uid, target.name)) }
        },
        playerSheet = playerSheet,
    )
}
