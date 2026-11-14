package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * Single-board replay: the game's read-only board centred at the largest size the space allows, the transport
 * under it at the board's width. Opens paused on the finished board. [showErrors] false hides every rule-violation mark.
 */
@Composable
fun ReplayPlayer(timeline: Timeline, board: GameReplayBoard, modifier: Modifier = Modifier, showErrors: Boolean = true) {
    val playback = rememberReplayPlayback(mine = timeline, theirs = null, showErrors = showErrors)
    val state by rememberStableReplayState(playback)
    val frame = state.mineFrame ?: return
    val options = rememberReplayBoardOptions(state)
    ReplayStage(
        boardCount = 1,
        modifier = modifier,
        controls = { ReplayTransport(state, playback) },
        board = { _, size -> Box(Modifier.size(size)) { board.Board(frame, options, Modifier.fillMaxWidth()) } },
    )
}
