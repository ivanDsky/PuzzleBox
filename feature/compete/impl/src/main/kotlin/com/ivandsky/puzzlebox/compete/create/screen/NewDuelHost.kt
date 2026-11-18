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
import com.ivandsky.puzzlebox.compete.create.rememberNewDuelActions
import com.ivandsky.puzzlebox.compete.share.DuelCreatedSheet
import com.ivandsky.puzzlebox.model.ShareCode
import org.koin.androidx.compose.koinViewModel

/**
 * New duel over its view model: [onClose] is Back, Cancel and Done; [onOpened] is a Past-you duel, which opens at once.
 * A [challenge] opens it filled in. A created friend duel shows "Duel created" over the screen. [pane] is for the tablet's pane.
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
    NewDuelScreen(state, actions, onBack = onClose, modifier = modifier, pane = pane)
    invite?.let { (text, link) -> DuelCreatedSheet(text = text, link = link, onDone = onClose) }
}
