package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.game.replay.ReplayFrame

/**
 * The playback state as the page recomposes on it: the same object until something a page draws changes, so a tick
 * that only moves the position recomposes nothing. The value is the first state of its run, so its
 * [ReplayPlaybackState.positionMs] and its frames' `atMs` are stale: read the position from [ReplayPlayback.state]
 * where it is drawn ([ReplayControls]), and a board reads the frame as the stable state hands it.
 */
@Composable
internal fun rememberStableReplayState(playback: ReplayPlayback): State<ReplayPlaybackState> {
    val live = playback.state.collectAsState()
    return remember(live) { derivedStateOf(SameDrawnState) { live.value } }
}

/** Two states are the same to a page when they differ only in the position and in frames that draw alike. */
internal object SameDrawnState : SnapshotMutationPolicy<ReplayPlaybackState> {
    override fun equivalent(a: ReplayPlaybackState, b: ReplayPlaybackState): Boolean =
        a.playing == b.playing && a.speed == b.speed && a.skipIdle == b.skipIdle && a.showErrors == b.showErrors &&
            a.seeked == b.seeked && a.durationMs == b.durationMs && a.ticksMs === b.ticksMs && a.tickKinds === b.tickKinds &&
            a.lead == b.lead && drawsAlike(a.mineFrame, b.mineFrame) && drawsAlike(a.theirsFrame, b.theirsFrame)

    /**
     * The same board, whatever the time inside it: the same session after the same group of moves, and on the same side of
     * the end and of the settle window (`sinceMoveMs`, which Tango reads). A board never reads `atMs` for more than
     * the direction of a step, and a frame kept for the length of its group still orders right against the next one.
     */
    private fun drawsAlike(a: ReplayFrame?, b: ReplayFrame?): Boolean {
        if (a == null || b == null) return a == null && b == null
        return a.session === b.session && a.before === b.before && a.ended == b.ended && a.moves == b.moves &&
            a.hinted == b.hinted && a.facts == b.facts && settled(a) == settled(b)
    }

    private fun settled(frame: ReplayFrame): Boolean = frame.sinceMoveMs >= MotionTokens.SettleMillis
}
