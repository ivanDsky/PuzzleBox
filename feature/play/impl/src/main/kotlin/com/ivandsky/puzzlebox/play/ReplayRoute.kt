package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ivandsky.puzzlebox.gameui.replay.ReplayCopy
import com.ivandsky.puzzlebox.gameui.replay.ReplayPage
import com.ivandsky.puzzlebox.play.api.ReplayKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The replay of [key]'s solve, full screen. A solve with no replay (any cause, or a game with no replay board) shows
 * [ReplayCopy.NO_REPLAY] under the same back bar instead of a player. Read-only: it only navigates back.
 */
@Composable
internal fun ReplayRoute(key: ReplayKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ReplayViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val board = viewModel.board
    Box(modifier.fillMaxSize().testTag("route.replay")) {
        when (val shown = state) {
            ReplayState.Loading -> Unit
            is ReplayState.Ready ->
                if (board != null) ReplayPage(shown.timeline, board, viewModel.context, onBack) else NoReplay(onBack)
            ReplayState.None -> NoReplay(onBack)
        }
    }
}

/** ReplayPage needs a timeline and has no empty state, so the one without a replay is drawn here. */
@Composable
private fun NoReplay(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CompactTopBar(
            title = ReplayCopy.TITLE,
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("replay.up")) {
                    Icon(PuzzleIcons.Back, contentDescription = ReplayCopy.BACK)
                }
            },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ReplayCopy.NO_REPLAY, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("replay.none"))
        }
    }
}
