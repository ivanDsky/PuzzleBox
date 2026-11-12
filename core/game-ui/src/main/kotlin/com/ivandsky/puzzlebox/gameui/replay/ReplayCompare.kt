package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * Two-sided replay section. Draws nothing when both timelines are null. Both sides play from time 0 on one
 * shared transport; a side that has ended stays on its last board. The lead line shows only when both sides
 * have a replay, and its noun is [board].pieces. Needs a bounded height, like [ReplayPlayer].
 */
@Composable
fun ReplayCompare(
    mine: Timeline?,
    theirs: Timeline?,
    board: GameReplayBoard,
    names: CompareNames,
    layout: CompareLayout,
    modifier: Modifier = Modifier,
    finalLead: Lead? = null,
) {
    if (mine == null && theirs == null) return
    val playback = rememberReplayPlayback(mine, theirs, finalLead)
    val state by playback.state.collectAsState()
    Column(modifier) {
        if (mine != null && theirs != null) {
            ReplayLeadLine(state, mine, theirs, names, board.pieces, Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
        }
        val stage = Modifier.weight(1f).fillMaxWidth()
        when (layout) {
            CompareLayout.Twin -> ReplayTwin(state, playback, mine, theirs, board, names, stage)
            // Ticket 65 replaces this with the flip-and-peek player: until then one board, mine first.
            CompareLayout.FlipPeek -> ReplayStage(
                boardCount = 1,
                modifier = stage,
                controlsHeight = CompareTransportHeight,
                controls = { ReplayCompareTransport(state, playback, mine, theirs, names) },
                board = { _, size -> ReplayBoardSlot(state.mineFrame ?: state.theirsFrame, board, size) },
            )
        }
    }
}
