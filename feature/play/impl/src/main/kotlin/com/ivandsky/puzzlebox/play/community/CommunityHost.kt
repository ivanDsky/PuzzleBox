package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.model.ShareCode
import org.koin.androidx.compose.koinViewModel

/**
 * Hosts Games › Custom's New today (nav-rethink C2). Called as `CommunityHost(onOpen) { community -> ... }`:
 * the content draws the Custom list and gets the section to add to it, or null when the community flag is off, so the
 * list is exactly what it was. The snackbar and the sign-in dialog sit over the content, outside its scrolling list.
 * `onOpen` opens a row's puzzle by its code, the way a friend's puzzle opens.
 *
 * The flag is read by [CommunityViewModel], once; this host never reads it. It is an object, not a top-level
 * composable, because it binds the Koin view model and so has no preview of its own; the section it adds is
 * previewed in `CommunityPreviews.kt`.
 */
object CommunityHost {

    @Composable
    operator fun invoke(onOpen: (code: String) -> Unit, content: @Composable (community: (LazyListScope.() -> Unit)?) -> Unit) {
        val viewModel: CommunityViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        if (!state.enabled) {
            content(null)
            return
        }
        CommunityBound(viewModel, state, onOpen = { code -> onOpen(code.value) }) { actions ->
            content { communitySection(state, actions) }
        }
    }
}

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
