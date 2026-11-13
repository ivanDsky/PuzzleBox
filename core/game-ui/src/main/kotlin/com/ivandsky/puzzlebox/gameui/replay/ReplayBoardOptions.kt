package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** How a replay board draws one frame, beyond the frame itself. */
class ReplayBoardOptions(
    /** The replay is playing and Reduce motion is off: a forward step may animate ([stepsForward]). Paused, scrubbing or stepping back, the board rests. */
    val animate: Boolean,
    /** False hides every rule-violation mark; errors are derived per frame, never stored. */
    val showErrors: Boolean,
)

/** The options of the boards under [state]: they animate only while the replay plays with Reduce motion off. */
@Composable
internal fun rememberReplayBoardOptions(state: ReplayPlaybackState): ReplayBoardOptions {
    val reduced = rememberReducedMotion()
    val animate = state.playing && !reduced
    return remember(animate, state.showErrors) { ReplayBoardOptions(animate, state.showErrors) }
}
