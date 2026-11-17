package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.replay.CompareLayout
import com.ivandsky.puzzlebox.gameui.replay.CompareNames
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayComparePage
import com.ivandsky.puzzlebox.gameui.replay.ReplayEmptyPage
import com.ivandsky.puzzlebox.gameui.replay.compareLayoutFor
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The compare of [key]'s two solves, full screen: [CompareLayout.Twin] at Expanded width with room for two boards,
 * [CompareLayout.FlipPeek] otherwise. No replay on either side (any cause, or a game with no replay board) shows the empty page under the same
 * back bar. Read-only: it only navigates back.
 */
@Composable
internal fun ReplayCompareRoute(key: ReplayCompareKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ReplayCompareViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showErrors by viewModel.showErrors.collectAsStateWithLifecycle()
    ZipDotPulseHost {
        ReplayCompareScreen(
            state = state,
            board = viewModel.board,
            names = viewModel.names,
            finalLead = viewModel.finalLead,
            context = viewModel.context,
            widthDp = LocalConfiguration.current.screenWidthDp,
            heightDp = LocalConfiguration.current.screenHeightDp,
            onBack = onBack,
            showErrors = showErrors,
            onShowErrorsChange = viewModel::setShowErrors,
            modifier = modifier,
        )
    }
}

/** The page of one [ReplayCompareState] at a window [widthDp] by [heightDp]. */
@Composable
internal fun ReplayCompareScreen(
    state: ReplayCompareState,
    board: GameReplayBoard?,
    names: CompareNames,
    finalLead: Lead?,
    context: String,
    widthDp: Int,
    heightDp: Int,
    onBack: () -> Unit,
    showErrors: Boolean,
    onShowErrorsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("route.replay-compare")) {
        when (state) {
            ReplayCompareState.Loading -> Unit
            is ReplayCompareState.Ready ->
                if (board != null) {
                    ReplayComparePage(state.mine, state.theirs, board, names, compareLayoutFor(widthDp.dp, heightDp.dp), context, onBack, showErrors, onShowErrorsChange, finalLead = finalLead)
                } else {
                    ReplayEmptyPage(context, onBack)
                }
            ReplayCompareState.None -> ReplayEmptyPage(context, onBack)
        }
    }
}
