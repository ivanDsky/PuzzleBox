package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * The full-screen replay: the flat top bar, then the player filling the rest. [context] is the page's
 * "Game · #N · day" line; the move count is appended to it. The top bar's Show errors switch is [showErrors];
 * [onShowErrorsChange] remembers it.
 */
@Composable
fun ReplayPage(
    timeline: Timeline,
    board: GameReplayBoard,
    context: String,
    onBack: () -> Unit,
    showErrors: Boolean,
    onShowErrorsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        ReplayTopBar(ReplayCopy.TITLE, ReplayCopy.subtitle(context, timeline.moveTimesMs.size), onBack) {
            ShowErrorsToggle(showErrors, onShowErrorsChange)
        }
        ReplayPlayer(timeline, board, Modifier.weight(1f).fillMaxSize().padding(bottom = Spacing.lg), showErrors)
    }
}
