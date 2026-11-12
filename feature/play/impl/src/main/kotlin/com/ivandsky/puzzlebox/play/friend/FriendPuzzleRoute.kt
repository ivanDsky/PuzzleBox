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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A friend's puzzle on the Play stack: a spinner while the code opens, then the scrolling
 * [FriendPuzzleCard], whose Play, Resume or Play again opens the puzzle's board ([onPlay]) and
 * whose board preview signs in with Google in place, or [FriendOpenError] saying why the code
 * did not open, with Try again or the store listing when either helps. Up is [onBack], the navigator's pop.
 */
@Composable
internal fun FriendPuzzleRoute(
    key: FriendPuzzleKey,
    onPlay: (CustomGameKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendPuzzleViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
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
                onSignIn = { viewModel.signIn(context) },
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
            )
            is FriendCardState.Failed -> card.outcome.errorCopy(ShareCode.parse(key.code))?.let { copy ->
                FriendOpenError(
                    copy = copy,
                    onRetry = viewModel::retry,
                    onUpdate = { uriHandler.openStoreListing() },
                )
            }
        }
    }
}
