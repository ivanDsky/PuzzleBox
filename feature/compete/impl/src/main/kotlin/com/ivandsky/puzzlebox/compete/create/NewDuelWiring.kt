package com.ivandsky.puzzlebox.compete.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ivandsky.puzzlebox.compete.duel.rememberCompeteTime
import com.ivandsky.puzzlebox.compete.share.createdInviteSaver
import com.ivandsky.puzzlebox.compete.share.createdInviteText
import kotlinx.coroutines.launch

/** Binds each of the panels' actions to [viewModel]; [onCancel] is the sign-in panel's Cancel. */
@Composable
internal fun rememberNewDuelActions(viewModel: NewDuelViewModel, onCancel: () -> Unit): NewDuelActions {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cancel by rememberUpdatedState(onCancel)
    return remember(viewModel, scope, context) {
        NewDuelActions(
            onQuick = viewModel::chooseQuick,
            onDaily = viewModel::chooseDaily,
            onToggle = viewModel::toggle,
            onDeadline = viewModel::setDeadline,
            onFriends = viewModel::setFriends,
            onOpponent = viewModel::chooseOpponent,
            onCreate = viewModel::create,
            onGoogle = { scope.launch { viewModel.signIn(context) } },
            onCancel = { cancel() },
            onCustomize = viewModel::chooseCustom,
            onEditLineup = viewModel::editLineup,
        )
    }
}

/** The created duel's invite text and link, once Create succeeded; saved across recreation. */
@Composable
internal fun rememberCreatedInvite(viewModel: NewDuelViewModel, inviter: String?): State<Pair<String, String>?> {
    val who by rememberUpdatedState(inviter)
    val time by rememberUpdatedState(rememberCompeteTime())
    val shared = rememberSaveable(stateSaver = createdInviteSaver) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.created.collect { created ->
            val games = viewModel.inviteGames(created)
            shared.value = createdInviteText(who, created.puzzles.size, games, created.deadlineHours, created.code, time) to created.code.matchLink
        }
    }
    return shared
}
