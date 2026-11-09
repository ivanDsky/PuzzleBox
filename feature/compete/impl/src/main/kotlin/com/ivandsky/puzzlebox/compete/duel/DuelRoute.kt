package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.navigation.Navigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The duel at [code]: the invite card for a viewer with no seat; a seat holder gets the puzzles screen (ticket 36). */
@Composable
internal fun DuelRoute(code: String, navigator: Navigator) {
    val vm: DuelViewModel = koinViewModel(key = code, parameters = { parametersOf(code) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.closes.collect { navigator.pop() } }
    val actions = InviteActions(
        onAccept = vm::accept,
        onDecline = vm::decline,
        onCancel = vm::cancel,
        onClose = navigator::pop,
        onSignIn = { scope.launch { vm.signIn(context) } },
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompactTopBar(
            title = "Duel",
            navigationIcon = {
                IconButton(onClick = navigator::pop, modifier = Modifier.testTag("duel.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back")
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (val current = state) {
                DuelUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is DuelUiState.Invite -> DuelInviteCard(current, actions, Modifier.verticalScroll(rememberScrollState()))
                // Ticket 36 replaces this with the puzzles screen.
                is DuelUiState.Participant -> EmptyState(
                    title = "Duel with ${opponentName(current)}",
                    message = "Your puzzles are on the way.",
                    actionLabel = "Close",
                    onAction = navigator::pop,
                )
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
}

private fun opponentName(state: DuelUiState.Participant): String {
    val match = state.view.match
    return (if (match.challenger.uid == state.view.me) match.opponent?.name else match.challenger.name) ?: "a friend"
}
