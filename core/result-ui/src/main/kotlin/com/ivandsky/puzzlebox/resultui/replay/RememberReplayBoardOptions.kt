package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplayPlaybackState

/** The options of the boards under [state]: they animate only while the replay plays, past the frame a seek landed on, with Reduce motion off. */
@Composable
internal fun rememberReplayBoardOptions(state: ReplayPlaybackState): ReplayBoardOptions {
    val reduced = rememberReducedMotion()
    val animate = state.playing && !state.seeked && !reduced
    return remember(animate, state.showErrors) { ReplayBoardOptions(animate, state.showErrors) }
}
