package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A friend's puzzle on the Play stack: a spinner while the code opens, then the scrolling
 * [FriendPuzzleCard], whose Play, Resume or Replay opens the puzzle's board ([onPlay]), or why
 * the code did not open. Up is [onBack], the navigator's pop.
 */
@Composable
internal fun FriendPuzzleRoute(
    key: FriendPuzzleKey,
    onPlay: (CustomGameKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FriendPuzzleViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().testTag("route.friend")) {
        CompactTopBar(
            title = "",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("friend.up")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        when (val card = state) {
            FriendCardState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("friend.card.loading"))
            }
            is FriendCardState.Ready -> FriendPuzzleCard(
                card = card.card,
                onAction = { onPlay(card.card.play) },
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
            )
            is FriendCardState.Failed -> Text(
                text = failedMessage(card.outcome),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(Spacing.lg).testTag("friend.card.failed"),
            )
        }
    }
}

private fun failedMessage(outcome: OpenOutcome): String = when (outcome) {
    is OpenOutcome.Opened -> ""
    OpenOutcome.NotFound -> "No puzzle with this code"
    OpenOutcome.NoLongerAvailable -> "This puzzle is no longer available"
    is OpenOutcome.NeedsUpdate, is OpenOutcome.UnknownGame -> "This puzzle needs a newer PuzzleBox"
    OpenOutcome.FailedCheck -> "This puzzle couldn't be checked"
    OpenOutcome.Offline -> "You're offline"
}
