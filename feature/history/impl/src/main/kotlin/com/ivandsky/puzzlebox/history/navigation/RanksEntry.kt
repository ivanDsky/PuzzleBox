package com.ivandsky.puzzlebox.history.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.history.historyCompareKey
import com.ivandsky.puzzlebox.history.ranks.PlayerSheetViewModel
import com.ivandsky.puzzlebox.history.ranks.RanksActions
import com.ivandsky.puzzlebox.history.ranks.RanksBoard
import com.ivandsky.puzzlebox.history.ranks.RanksScreen
import com.ivandsky.puzzlebox.history.ranks.RanksSelection
import com.ivandsky.puzzlebox.history.ranks.RanksViewModel
import com.ivandsky.puzzlebox.history.ranks.ReplayOptInViewModel
import com.ivandsky.puzzlebox.history.ranks.hasOfficialOwnRow
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

/**
 * The Ranks hub's routes: the tab root [HistoryRootKey] and the pushed [HistoryKey]
 * (a game's link, with a back arrow), which opens the hub at that game and day (A4).
 * [navigator] opens a day's game and its replays from the Your solve row; [layout] picks
 * the tablet's two panes. [linkedIn] adds the connections' ghosts to a game's Friends day board, from what the phone
 * holds; off, the hub touches nothing of LinkedIn.
 * A pushed key's [HistoryKey.calendarExpanded] opens the hub's calendar to the month. A past day's Your solve
 * offers a play action, the bar's tune label opens the Tune sheet and the tablet's hero card jumps to the viewer's row;
 * [tuneDefaults] is the player's defaults the sheet shows, unless [com.ivandsky.puzzlebox.history.flags.HistoryFlag.RanksTuneBoardOnly]
 * leaves them to Settings.
 */
fun EntryProviderScope<NavKey>.ranksEntry(
    navigator: Navigator,
    layout: HistoryLayout,
    linkedIn: Boolean = false,
    tuneDefaults: RanksTuneDefaults,
) {
    entry<HistoryRootKey> {
        RanksRoute(navigator, layout, linkedIn, RanksSelection(), key = null, onBack = null, tuneDefaults = tuneDefaults)
    }
    entry<HistoryKey> { key ->
        val opening = RanksSelection.opening(
            board = key.gameId?.let { RanksBoard.Game(GameId(it)) } ?: RanksBoard.Overall,
            day = key.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            calendarExpanded = key.calendarExpanded,
        )
        RanksRoute(navigator, layout, linkedIn, opening, key = key.toString(), onBack = { navigator.pop() }, tuneDefaults = tuneDefaults)
    }
}

/**
 * One hub per entry: [key] keeps a pushed hub's state apart from the root's; [opening] is where a fresh one starts. The
 * hub's three view models end here: the screen below takes their state and reports its taps.
 */
@Composable
private fun RanksRoute(
    navigator: Navigator,
    layout: HistoryLayout,
    linkedIn: Boolean,
    opening: RanksSelection,
    key: String?,
    onBack: (() -> Unit)?,
    tuneDefaults: RanksTuneDefaults,
) {
    // One key per class: a ViewModelStore keeps a key's last ViewModel only, so a shared key made each new one clear the one before it.
    val viewModel = koinViewModel<RanksViewModel>(key = key?.let { "$it/ranks" }, parameters = { parametersOf(linkedIn, opening) })
    val playerSheet = koinViewModel<PlayerSheetViewModel>(key = key?.let { "$it/playerSheet" })
    val replayOptIn = koinViewModel<ReplayOptInViewModel>(key = key?.let { "$it/replayOptIn" })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val openPlayer by playerSheet.state.collectAsStateWithLifecycle()
    // The opt-in asks only once the board on screen holds the viewer's official row.
    val ranked = state.board.state.hasOfficialOwnRow()
    LaunchedEffect(ranked) { replayOptIn.rankedRow(ranked) }
    val askReplays by replayOptIn.show.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playGame = { game: GameId, day: LocalDate -> navigator.pushOnce(GameKey(game.value, day.toString())) }
    RanksScreen(
        state = state,
        actions = RanksActions(
            onBack = onBack,
            onReset = viewModel::resetSelection,
            onSignIn = { scope.launch { viewModel.signIn(context) } },
            onSwipe = viewModel::swipe,
            onPickBoard = viewModel::pickBoard,
            onNudgeShown = viewModel::markSwipeNudgeSeen,
            onScope = viewModel::setScope,
            onWindow = viewModel::setWindow,
            onGames = viewModel::setGames,
            onShowCalendar = viewModel::showCalendar,
            onSelectDay = viewModel::selectDay,
            onPlayAgain = playGame,
            onPlayThisDay = playGame,
            onOpenReplay = { row, solve ->
                navigator.pushOnce(ReplayKey.daily(row.gameId.value, row.day.toString(), row.puzzleNumber, latest = solve == ReplaySolve.Latest))
            },
            onCompare = { row -> navigator.pushOnce(historyCompareKey(row)) },
            onWatchPlayer = { target ->
                target.board?.let { navigator.pushOnce(ReplayKey.player(it.gameId, it.day.toString(), target.uid, target.name)) }
            },
            onOpenPlayer = playerSheet::open,
            onChallenge = playerSheet::challenge,
            onClosePlayer = playerSheet::close,
            onShareReplays = replayOptIn::share,
            onKeepReplaysPrivate = replayOptIn::keepPrivate,
            onSheetOpen = replayOptIn::hold,
            onRefresh = viewModel::refresh,
        ),
        layout = layout,
        playerSheet = openPlayer,
        replayOptIn = askReplays,
        refreshing = refreshing,
        tuneDefaults = tuneDefaults.takeUnless { viewModel.tuneBoardOnly },
    )
}
