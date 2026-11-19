package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * New today's actions bound to [viewModel], handed to [content]; the snackbar and the sign-in dialog sit over it,
 * outside its scrolling list. A row opens through [onOpen].
 */
@Composable
internal fun CommunityBound(
    viewModel: CommunityViewModel,
    state: CommunityState,
    onOpen: (ShareCode) -> Unit,
    content: @Composable (CommunityActions) -> Unit,
) {
    val context = LocalContext.current
    val actions = CommunityActions(
        onOpen = onOpen,
        onReport = viewModel::report,
        onGame = viewModel::pickGame,
        onDifficulty = viewModel::pickDifficulty,
        onClearFilters = viewModel::clearFilters,
        onRetry = viewModel::retry,
    )
    Box(Modifier.fillMaxSize()) {
        content(actions)
        CommunityNoticeSnackbar(state.notice, viewModel::noticeShown, Modifier.align(Alignment.BottomCenter))
        if (state.signInPrompt) {
            CommunitySignInDialog(state.signIn, onSignIn = { viewModel.signIn(context) }, onDismiss = viewModel::dismissSignIn)
        }
    }
}

/** Shows [notice] once, then says it is [onShown]. */
@Composable
private fun CommunityNoticeSnackbar(notice: CommunityNotice?, onShown: (Int) -> Unit, modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(notice.message)
        onShown(notice.id)
    }
    SnackbarHost(host, modifier) { data -> Snackbar(data, Modifier.testTag("community.notice")) }
}
