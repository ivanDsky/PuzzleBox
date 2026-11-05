package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

/**
 * A letter in the live trace, over any base look: the beige selection token, and NYT's
 * `Pulse` as the letter joins (1 → [StrandsMotion.PressScale] → 1).
 */
internal object TraceOverlaySpec : StrandsCellSpec {
    private val PulseAt = floatArrayOf(0f, 0.5f, 1f)
    private val PulseScale = floatArrayOf(1f, StrandsMotion.PressScale, 1f)

    override fun fill(colors: BoardColors): Color = colors.strandsSelectionFill

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.SELECTED

    override fun motionMs(cue: StrandsMotionCue): Int =
        StrandsMotion.PressPulse.enterDurationMillis + StrandsMotion.PressPulse.exitDurationMillis

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame {
        val total = motionMs(cue)
        if (reduceMotion || elapsedMs >= total) return StrandsCellFrame.Rest
        return StrandsCellFrame(scale = keyframes(elapsedMs.toFloat() / total, PulseAt, PulseScale, StrandsMotion.PressPulse.enterEasing))
    }
}
