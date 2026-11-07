package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A friend's puzzle card on the Play stack: its title, Solved once a solve is recorded here,
 * and Play, which opens the puzzle's board ([onPlay]). Up is [onBack], the navigator's pop.
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
            is FriendCardState.Ready -> ReadyCard(card, onPlay = { onPlay(card.play) })
            is FriendCardState.Failed -> Text(
                text = failedMessage(card.outcome),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(Spacing.lg).testTag("friend.card.failed"),
            )
        }
    }
}

@Composable
private fun ReadyCard(card: FriendCardState.Ready, onPlay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = card.puzzle.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("friend.card.title"),
        )
        if (card.solved) {
            Text(
                text = "Solved",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("friend.card.solved"),
            )
        }
        PrimaryButton(text = "Play", onClick = onPlay, modifier = Modifier.fillMaxWidth().testTag("friend.card.play"))
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
