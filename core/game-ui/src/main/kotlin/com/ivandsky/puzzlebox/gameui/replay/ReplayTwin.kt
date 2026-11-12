package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * Both boards side by side, each under its side's name, with the one compare transport below. Mine is on the
 * left. A side without a replay shows "No replay" in its place; its board never appears empty.
 */
@Composable
internal fun ReplayTwin(
    state: ReplayPlaybackState,
    playback: ReplayPlayback,
    mine: Timeline?,
    theirs: Timeline?,
    board: GameReplayBoard,
    names: CompareNames,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ReplayStage(
        boardCount = 2,
        modifier = modifier,
        controlsHeight = CompareTransportHeight,
        boardHeaderHeight = SideHeaderHeight,
        controls = { ReplayCompareTransport(state, playback, mine, theirs, names) },
        board = { index, size ->
            val isMine = index == 0
            val frame = if (isMine) state.mineFrame else state.theirsFrame
            Column(Modifier.width(size)) {
                ReplaySideHeader(
                    name = if (isMine) names.mine else names.theirs,
                    facts = frame?.facts,
                    pieces = board.pieces,
                    tint = if (isMine) colors.primary else colors.tertiary,
                    onTint = if (isMine) colors.onPrimary else colors.onTertiary,
                )
                ReplayBoardSlot(frame, board, size)
            }
        },
    )
}
