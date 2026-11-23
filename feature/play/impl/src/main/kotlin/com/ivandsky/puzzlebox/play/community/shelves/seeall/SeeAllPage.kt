package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunitySignInDialog
import org.koin.androidx.compose.koinViewModel

/**
 * The See all page for [scope] bound to its view model: it opens the scope (clean on a new [visit], as it was left when
 * the same one opens again), keeps [bottomPadding] clear under the list and the notice, keeps the list's place for the
 * visit (a row opened and come back to, a rotation), and puts the report notice and the
 * sign-in dialog over the screen. A row opens through [onOpen]; [onBack] leaves the page.
 */
@Composable
internal fun SeeAllPage(
    scope: SeeAllScope,
    onBack: () -> Unit,
    onOpen: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
    visit: Int = 0,
    bottomPadding: Dp = 0.dp,
    removedFriends: Set<ShareCode> = emptySet(),
    viewModel: SeeAllViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val listState = rememberSaveable(scope, visit, saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(scope, visit) { viewModel.open(scope, visit) }
    LaunchedEffect(removedFriends) { viewModel.friendsRemoved(removedFriends) }
    val held by viewModel.state.collectAsState()
    // The view model outlives a page: until it has opened this scope it still holds the last one's, so show a fresh load.
    val state = if (held.scope == scope) held else SeeAllUiState(scope = scope, games = held.games, showGameFilter = scope !is SeeAllScope.Game)
    val actions = SeeAllActions(
        onBack = onBack,
        onQuery = viewModel::setQuery,
        onGame = viewModel::setGame,
        onDifficulty = viewModel::setDifficulty,
        onClearFilters = viewModel::clearFilters,
        onOpen = onOpen,
        onReport = viewModel::report,
        onRetry = viewModel::retry,
    )
    Box(modifier.fillMaxSize()) {
        SeeAllScreen(state, actions, bottomPadding = bottomPadding, listState = listState)
        SeeAllNotice(state.notice, viewModel::noticeShown, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        if (state.signInPrompt) {
            CommunitySignInDialog(state.signIn, onSignIn = { viewModel.signIn(context) }, onDismiss = viewModel::dismissSignIn)
        }
    }
}
