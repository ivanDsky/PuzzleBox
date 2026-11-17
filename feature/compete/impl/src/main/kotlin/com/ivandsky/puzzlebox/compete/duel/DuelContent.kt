package com.ivandsky.puzzlebox.compete.duel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.compete.share.shareInvite
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The duel at [code] without a title bar: the invite card for a viewer with no seat; a seat holder gets the puzzles
 * screen. It is the whole of [DuelRoute] under its bar and the tablet's right pane ([com.ivandsky.puzzlebox.compete.tab.DuelsPane]).
 * [onClose] leaves the duel: the invite's Close, and a decline or cancel that ends it. Start, Resume and See results
 * push their screens on [navigator], over whichever panes are showing.
 */
@Composable
internal fun DuelContent(code: String, navigator: Navigator, layout: PlayLayout, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val vm: DuelViewModel = koinViewModel(key = code, parameters = { parametersOf(code) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val time = rememberCompeteTime()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(vm) { vm.closes.collect { currentOnClose() } }
    val actions = InviteActions(
        onAccept = vm::accept,
        onDecline = vm::decline,
        onCancel = vm::cancel,
        onClose = onClose,
        onSignIn = { scope.launch { vm.signIn(context) } },
    )
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        when (val current = state) {
            DuelUiState.Loading -> DuelLoading(layout)
            is DuelUiState.Invite -> DuelInviteCard(current, actions, Modifier.verticalScroll(rememberScrollState()))
            is DuelUiState.Participant -> {
                val now by rememberCountdownNow(time, current.view.match.closesAt)
                DuelLegs(current, layout, now, time, legsActions(current, context, navigator), Modifier.fillMaxSize())
            }
            DuelUiState.NotFound -> EmptyState("This duel link doesn't work", "Check the code with your friend and try again.")
            DuelUiState.Offline -> ErrorState(
                title = "You're offline",
                message = "A duel needs a connection to open. Check it and try again.",
                onRetry = vm::retry,
                modifier = Modifier.align(Alignment.Center),
            )
            DuelUiState.UpdateApp -> EmptyState("Update PuzzleBox", "This duel needs a newer version of the app. Update it to open the duel.")
        }
    }
}

/** Start and Resume push the puzzle's play screen (the game id from the match's own puzzle); the footer buttons share and open the reveal. */
private fun legsActions(state: DuelUiState.Participant, context: Context, navigator: Navigator) = LegsActions(
    onOpenLeg = { row ->
        val match = state.view.match
        navigator.pushOnce(MatchLegKey(match.code.value, row.legIndex, match.legs[row.legIndex].ref.gameId.value))
    },
    onNudge = context::shareInvite,
    onReveal = { navigator.push(DuelRevealKey(state.view.match.code.value)) },
)
