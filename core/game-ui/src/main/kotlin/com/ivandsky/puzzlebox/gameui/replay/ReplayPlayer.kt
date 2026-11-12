package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.Timeline

/** Single-board replay: the game's read-only board with the transport under it. Opens paused on the finished board. */
@Composable
fun ReplayPlayer(timeline: Timeline, board: GameReplayBoard, modifier: Modifier = Modifier) {
    val playback = rememberReplayPlayback(mine = timeline, theirs = null)
    val state by playback.state.collectAsState()
    val frame = state.mineFrame ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        board.Board(frame, Modifier.fillMaxWidth())
        ReplayTransport(state, playback)
    }
}
