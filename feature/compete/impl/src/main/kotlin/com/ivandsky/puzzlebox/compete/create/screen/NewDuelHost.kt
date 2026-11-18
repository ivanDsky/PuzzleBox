package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.create.NewDuelViewModel
import com.ivandsky.puzzlebox.compete.create.rememberCreatedInvite
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.PAST_YOU_PICKER
import com.ivandsky.puzzlebox.compete.create.lineup.PuzzlePickerPage
import com.ivandsky.puzzlebox.compete.create.rememberNewDuelActions
import com.ivandsky.puzzlebox.compete.share.DuelCreatedSheet
import com.ivandsky.puzzlebox.model.ShareCode
import org.koin.androidx.compose.koinViewModel

/**
 * New duel over its view model: [onClose] is Back, Cancel and Done; [onOpened] is a Past-you duel, which opens at once.
 * A [challenge] opens it filled in. A created friend duel shows "Duel created" over the screen. [pane] is for the tablet's pane.
 * The two puzzle searches, a friend's and Past you's, live here: the screen's picker page uses the one for the duel's
 * opponent, and both start clean each time the page closes.
 */
@Composable
internal fun NewDuelHost(
    challenge: DuelChallenge?,
    onClose: () -> Unit,
    onOpened: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
    pane: Boolean = false,
    viewModel: NewDuelViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberNewDuelActions(viewModel, onCancel = onClose)
    val invite by rememberCreatedInvite(viewModel, viewModel.inviter())
    val opened by rememberUpdatedState(onOpened)
    LaunchedEffect(viewModel) { viewModel.refreshSolved() }
    LaunchedEffect(viewModel, challenge) { challenge?.let(viewModel::prefill) }
    LaunchedEffect(viewModel) { viewModel.opened.collect { opened(it) } }
    // Under the same owner as the screen, so the query outlives a rotation; the qualifier keeps Past you's from the friend's text and rows.
    val friendSearch = koinViewModel<AddPuzzleViewModel>()
    val pastYouSearch = koinViewModel<AddPuzzleViewModel>(qualifier = PAST_YOU_PICKER)
    val picking = state.lineup.target != null
    LaunchedEffect(picking) {
        if (!picking) {
            friendSearch.reset()
            pastYouSearch.reset()
        }
    }
    NewDuelScreen(state, actions, onBack = onClose, modifier = modifier, pane = pane) { picker ->
        PuzzlePickerPage(
            picker = picker,
            onPick = { entry -> actions.onEditLineup { it.pick(entry) } },
            viewModel = if (picker.pastYou) pastYouSearch else friendSearch,
        )
    }
    invite?.let { (text, link) -> DuelCreatedSheet(text = text, link = link, onDone = onClose) }
}
