package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * Single-board replay: the game's read-only board centred at the largest size the space allows, the transport
 * under it at the board's width. Opens paused on the finished board.
 */
@Composable
fun ReplayPlayer(timeline: Timeline, board: GameReplayBoard, modifier: Modifier = Modifier) {
    val playback = rememberReplayPlayback(mine = timeline, theirs = null)
    val state by playback.state.collectAsState()
    val frame = state.mineFrame ?: return
    ReplayStage(
        boardCount = 1,
        modifier = modifier,
        controls = { ReplayTransport(state, playback) },
        board = { _, size -> Box(Modifier.size(size)) { board.Board(frame, Modifier.fillMaxWidth()) } },
    )
}
