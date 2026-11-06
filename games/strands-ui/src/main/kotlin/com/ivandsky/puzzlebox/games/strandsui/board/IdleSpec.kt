package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.BoardColors

/** An unused letter: no token, just the letter on the bare board (NYT idle). */
internal object IdleSpec : StrandsCellSpec {
    override fun fill(colors: BoardColors): Color? = null

    override fun letter(colors: BoardColors): Color = colors.strandsIdleLetter

    override val stateWord: String? = null

    override fun motionMs(cue: StrandsMotionCue): Int = 0

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean) = StrandsCellFrame.Rest
}
